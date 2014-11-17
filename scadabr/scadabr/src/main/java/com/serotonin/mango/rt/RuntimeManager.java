/*
 Mango - Open Source M2M - http://mango.serotoninsoftware.com
 Copyright (C) 2006-2011 Serotonin Software Technologies Inc.
 @author Matthew Lohbihler
    
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.serotonin.mango.rt;

import br.org.scadabr.DataType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;

import br.org.scadabr.ShouldNeverHappenException;
import com.serotonin.mango.db.dao.DataPointDao;
import com.serotonin.mango.db.dao.DataSourceDao;
import com.serotonin.mango.db.dao.PointValueDao;
import com.serotonin.mango.rt.dataImage.DataPointEventMulticaster;
import com.serotonin.mango.rt.dataImage.DataPointListener;
import com.serotonin.mango.rt.dataImage.DataPointRT;
import com.serotonin.mango.rt.dataImage.PointValueTime;
import com.serotonin.mango.rt.dataImage.SetPointSource;
import com.serotonin.mango.rt.dataImage.types.MangoValue;
import com.serotonin.mango.rt.dataSource.DataSourceRT;
import com.serotonin.mango.rt.dataSource.meta.MetaDataSourceRT;
import com.serotonin.mango.vo.DataPointVO;
import com.serotonin.mango.vo.dataSource.DataSourceVO;
import com.serotonin.mango.vo.event.PointEventDetectorVO;
import br.org.scadabr.utils.TimePeriods;
import com.serotonin.mango.web.UserSessionContextBean;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;

@Named
public class RuntimeManager {

    private static final Log LOG = LogFactory.getLog(RuntimeManager.class);

    private final List<DataSourceRT> runningDataSources = new CopyOnWriteArrayList<>();
    private final Set<UserSessionContextBean> userSessions = new HashSet<>();

    /**
     * Provides a quick lookup map of the running data points.
     */
    private final Map<Integer, DataPointRT> dataPoints = new ConcurrentHashMap<>();

    /**
     * The list of point listeners, kept here such that listeners can be
     * notified of point initializations (i.e. a listener can register itself
     * before the point is enabled).
     */
    private final Map<Integer, DataPointListener> dataPointListeners = new ConcurrentHashMap<>();



    private boolean started = false;

    @Inject
    private DataSourceDao dataSourceDao;
    @Inject
    private DataPointDao dataPointDao;
    @Inject
    private PointValueDao pointValueDao;
    @Inject
    private EventManager eventManager;

    public RuntimeManager() {

    }

    //
    // Lifecycle
    synchronized public void initialize(boolean safe) {
        if (started) {
            throw new ShouldNeverHappenException(
                    "RuntimeManager already started");
        }

        // Set the started indicator to true.
        started = true;

        // Initialize data sources that are enabled.
        List<DataSourceVO<?>> pollingRound = new ArrayList<>();
        for (DataSourceVO<?> config : dataSourceDao.getDataSources()) {
            if (config.isEnabled()) {
                if (safe) {
                    config.setEnabled(false);
                    dataSourceDao.saveDataSource(config);
                } else if (initializeDataSource(config)) {
                    pollingRound.add(config);
                }
            }
        }

        // Tell the data sources to start polling. Delaying the polling start
        // gives the data points a chance to
        // initialize such that point listeners in meta points and set point
        // handlers can run properly.
        for (DataSourceVO<?> config : pollingRound) {
            startDataSourcePolling(config);
        }
    }

    synchronized public void terminate() {
        if (!started) {
            throw new ShouldNeverHappenException(
                    "RuntimeManager not yet started");
        }

        started = false;
        // First stop meta data sources.
        for (DataSourceRT dataSource : runningDataSources) {
            if (dataSource instanceof MetaDataSourceRT) {
                stopDataSource(dataSource.getId());
            }
        }
        // Then stop everything else.
        for (DataSourceRT dataSource : runningDataSources) {
            stopDataSource(dataSource.getId());
        }

    }

    public void joinTermination() {
        for (DataSourceRT dataSource : runningDataSources) {
            try {
                dataSource.joinTermination();
            } catch (ShouldNeverHappenException e) {
                LOG.error("Error stopping data source " + dataSource.getId(), e);
            }
        }
    }

    //
    //
    // Data sources
    //
    public DataSourceRT getRunningDataSource(int dataSourceId) {
        for (DataSourceRT dataSource : runningDataSources) {
            if (dataSource.getId() == dataSourceId) {
                return dataSource;
            }
        }
        return null;
    }

    public boolean isDataSourceRunning(int dataSourceId) {
        return getRunningDataSource(dataSourceId) != null;
    }

    public List<DataSourceVO<?>> getDataSources() {
        return dataSourceDao.getDataSources();
    }

    public DataSourceVO<?> getDataSource(int dataSourceId) {
        return dataSourceDao.getDataSource(dataSourceId);
    }

    public void deleteDataSource(int dataSourceId) {
        stopDataSource(dataSourceId);
        eventManager.cancelEventsForDataSource(dataSourceId);
        dataSourceDao.deleteDataSource(dataSourceId);
    }

    public void saveDataSource(DataSourceVO<?> vo) {
        // If the data source is running, stop it.
        stopDataSource(vo.getId());

        // In case this is a new data source, we need to save to the database
        // first so that it has a proper id.
        dataSourceDao.saveDataSource(vo);

        // If the data source is enabled, start it.
        if (vo.isEnabled()) {
            if (initializeDataSource(vo)) {
                startDataSourcePolling(vo);
            }
        }
    }

    private boolean initializeDataSource(DataSourceVO<?> vo) {
        synchronized (runningDataSources) {
            // If the data source is already running, just quit.
            if (isDataSourceRunning(vo.getId())) {
                return false;
            }

            // Ensure that the data source is enabled.
            Assert.isTrue(vo.isEnabled());

            // Create the runtime version of the data source.
            final DataSourceRT dataSource = vo.createDataSourceRT();

            // Add it to the list of running data sources.
            runningDataSources.add(dataSource);

            // Add the enabled points to the data source.
            for (DataPointVO dataPoint : dataPointDao.getDataPoints(vo.getId(), null)) {
                if (dataPoint.isEnabled()) {
                    startDataPoint(dataPoint);
                } else {
                    dataSource.dataPointDisabled(dataPoint);
                }
            }

            // Initialize and thus start the runtime version of the data source.
            dataSource.initialize();

            LOG.info("Data source '" + vo.getName() + "' initialized");

            return true;
        }
    }

    private void startDataSourcePolling(DataSourceVO<?> vo) {
        DataSourceRT dataSource = getRunningDataSource(vo.getId());
        if (dataSource != null) {
            dataSource.beginPolling();
        }
    }

    public void stopDataSource(int id) {
        synchronized (runningDataSources) {
            DataSourceRT dataSource = getRunningDataSource(id);
            if (dataSource == null) {
                return;
            }

            // Stop the data points.
            for (DataPointRT p : dataPoints.values()) {
                if (p.getDataSourceId() == id) {
                    stopDataPoint(p.getVo());
                }
            }

            runningDataSources.remove(dataSource);
            dataSource.terminate();

            dataSource.joinTermination();
            LOG.info("Data source '" + dataSource.getName() + "' stopped");
        }
    }

    //
    //
    // Data points
    //
    public DataPointRT saveDataPoint(DataPointVO point) {
        stopDataPoint(point);

        // Since the point's data type may have changed, we must ensure that the
        // other attrtibutes are still ok with
        // it.
        DataType dataType = point.getDataType();

        // Chart renderer
        if (point.getChartRenderer() != null
                && !point.getChartRenderer().getType().supports(dataType)) // Return to a default renderer
        {
            point.setChartRenderer(null);
        }

        // Text renderer
        if (point.getTextRenderer() != null
                && !point.getTextRenderer().getDef().supports(dataType)) // Return to a default renderer
        {
            point.defaultTextRenderer();
        }

        // Event detectors
        final Iterator<PointEventDetectorVO> peds = point.getEventDetectors().iterator();
        while (peds.hasNext()) {
            PointEventDetectorVO ped = peds.next();
            if (!ped.getDef().supports(dataType)) {
                // Remove the detector.
                peds.remove();
            }
        }

        dataPointDao.saveDataPoint(point);

        if (point.isEnabled()) {
            return startDataPoint(point);
        } else {
            addDisabledDataPointToRT(point);
            return null;
        }
    }

    public void deleteDataPoint(DataPointVO point) {
        if (point.isEnabled()) {
            stopDataPoint(point);
        }
        final DataSourceRT dsRt = getRunningDataSource(point.getDataSourceId());
        if (dsRt != null) {
            dsRt.dataPointDeleted(point);
        }
        dataPointDao.deleteDataPoint(point.getId());
        eventManager.cancelEventsForDataPoint(point.getId());
    }

    private DataPointRT startDataPoint(DataPointVO vo) {
        synchronized (dataPoints) {
            Assert.isTrue(vo.isEnabled());

            // Only add the data point if its data source is enabled.
            DataSourceRT ds = getRunningDataSource(vo.getDataSourceId());
            if (ds != null) {
                // Change the VO into a data point implementation.
                DataPointRT dataPoint = new DataPointRT(vo, vo.getPointLocator().createRuntime());

                // Add/update it in the data image.
                dataPoints.put(dataPoint.getId(), dataPoint);

                // Initialize it.
                dataPoint.initialize();
                DataPointListener l = getDataPointListeners(vo.getId());
                if (l != null) {
                    l.pointInitialized();
                }

                // Add/update it in the data source.
                ds.dataPointEnabled(dataPoint);
                return dataPoint;
            }
        }
        return null;
    }

    /**
     * add a disabled datapoint to a running datasource
     *
     * @param vo
     * @return
     */
    private void addDisabledDataPointToRT(DataPointVO vo) {
        synchronized (dataPoints) {
            Assert.isTrue(!vo.isEnabled());

            // Only add the data point if its data source is enabled.
            DataSourceRT ds = getRunningDataSource(vo.getDataSourceId());
            if (ds != null) {
                ds.dataPointDisabled(vo);
            }
        }
    }

    private void stopDataPoint(DataPointVO dpVo) {
        synchronized (dataPoints) {
            // Remove this point from the data image if it is there. If not,
            // just quit.
            DataPointRT p = dataPoints.remove(dpVo.getId());

            // Remove it from the data source, and terminate it.
            if (p != null) {
                getRunningDataSource(p.getDataSourceId()).dataPointDisabled(p.getVo());
                DataPointListener l = getDataPointListeners(dpVo.getId());
                if (l != null) {
                    l.pointTerminated();
                }
                p.terminate();
            }
        }
    }

    public boolean isDataPointRunning(int dataPointId) {
        return dataPoints.get(dataPointId) != null;
    }

    public DataPointRT getDataPoint(int dataPointId) {
        return dataPoints.get(dataPointId);
    }

    public void addDataPointListener(int dataPointId, DataPointListener l) {
        DataPointListener listeners = dataPointListeners.get(dataPointId);
        dataPointListeners.put(dataPointId,
                DataPointEventMulticaster.add(listeners, l));
    }

    public void removeDataPointListener(int dataPointId, DataPointListener l) {
        DataPointListener listeners = DataPointEventMulticaster.remove(
                dataPointListeners.get(dataPointId), l);
        if (listeners == null) {
            dataPointListeners.remove(dataPointId);
        } else {
            dataPointListeners.put(dataPointId, listeners);
        }
    }

    public DataPointListener getDataPointListeners(int dataPointId) {
        return dataPointListeners.get(dataPointId);
    }

    //
    // Point values
    public void setDataPointValue(int dataPointId, MangoValue value,
            SetPointSource source) {
        setDataPointValue(dataPointId,
                new PointValueTime(value, System.currentTimeMillis()), source);
    }

    public void setDataPointValue(int dataPointId, PointValueTime valueTime,
            SetPointSource source) {
        DataPointRT dataPoint = dataPoints.get(dataPointId);
        if (dataPoint == null) {
            throw new RTException("Point is not enabled");
        }

        if (!dataPoint.getPointLocator().isSettable()) {
            throw new RTException("Point is not settable");
        }

        // Tell the data source to set the value of the point.
        DataSourceRT ds = getRunningDataSource(dataPoint.getDataSourceId());
        // The data source may have been disabled. Just make sure.
        if (ds != null) {
            ds.setPointValue(dataPoint, valueTime, source);
        }
    }

    public void relinquish(int dataPointId) {
        DataPointRT dataPoint = dataPoints.get(dataPointId);
        if (dataPoint == null) {
            throw new RTException("Point is not enabled");
        }

        if (!dataPoint.getPointLocator().isSettable()) {
            throw new RTException("Point is not settable");
        }
        if (!dataPoint.getPointLocator().isRelinquishable()) {
            throw new RTException("Point is not relinquishable");
        }

        // Tell the data source to relinquish value of the point.
        DataSourceRT ds = getRunningDataSource(dataPoint.getDataSourceId());
        // The data source may have been disabled. Just make sure.
        if (ds != null) {
            ds.relinquish(dataPoint);
        }
    }

    public void forcePointRead(int dataPointId) {
        DataPointRT dataPoint = dataPoints.get(dataPointId);
        if (dataPoint == null) {
            throw new RTException("Point is not enabled");
        }

        // Tell the data source to read the point value;
        DataSourceRT ds = getRunningDataSource(dataPoint.getDataSourceId());
        if (ds != null) // The data source may have been disabled. Just make sure.
        {
            ds.forcePointRead(dataPoint);
        }
    }

    public long purgeDataPointValues() {
        long count = pointValueDao.deleteAllPointData();
        pointValueDao.compressTables();
        for (Integer id : dataPoints.keySet()) {
            updateDataPointValuesRT(id);
        }
        return count;
    }

    public long purgeDataPointValues(int dataPointId, TimePeriods periodType,
            int periodCount) {
        long before = periodType.minus(System.currentTimeMillis(), periodCount);
        return purgeDataPointValues(dataPointId, before);
    }

    public long purgeDataPointValues(int dataPointId) {
        long count = pointValueDao.deletePointValues(dataPointId);
        updateDataPointValuesRT(dataPointId);
        return count;
    }

    public long purgeDataPointValues(int dataPointId, long before) {
        long count = pointValueDao.deletePointValuesBefore(dataPointId,
                before);
        if (count > 0) {
            updateDataPointValuesRT(dataPointId);
        }
        return count;
    }

    private void updateDataPointValuesRT(int dataPointId) {
        DataPointRT dataPoint = dataPoints.get(dataPointId);
        if (dataPoint != null) // Enabled. Reset the point's cache.
        {
            dataPoint.resetValues();
        }
    }

    public void addPointToHierarchy(DataPointVO dp, String... pathToPoint) {
        dataPointDao.addPointToHierarchy(dp, pathToPoint);
    }
    
    public void UserSessionStarts(UserSessionContextBean us) {
        userSessions.add(us);
    }
    
    public void UserSessionEnds(UserSessionContextBean us) {
        userSessions.remove(us);
    }
    
    public Set<UserSessionContextBean> getUserSessionContextBeans() {
        return userSessions;
    }
    
}
