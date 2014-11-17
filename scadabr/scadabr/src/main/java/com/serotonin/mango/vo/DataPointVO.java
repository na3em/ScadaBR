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
package com.serotonin.mango.vo;

import br.org.scadabr.DataType;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import br.org.scadabr.InvalidArgumentException;
import br.org.scadabr.ShouldNeverHappenException;
import br.org.scadabr.json.JsonArray;
import br.org.scadabr.json.JsonException;
import br.org.scadabr.json.JsonObject;
import br.org.scadabr.json.JsonReader;
import br.org.scadabr.json.JsonRemoteEntity;
import br.org.scadabr.json.JsonRemoteProperty;
import br.org.scadabr.json.JsonSerializable;
import br.org.scadabr.json.JsonValue;
import com.serotonin.mango.Common;
import com.serotonin.mango.db.dao.DataPointDao;
import com.serotonin.mango.rt.dataImage.PointValueTime;
import com.serotonin.mango.rt.dataImage.types.MangoValue;
import com.serotonin.mango.rt.event.type.AuditEventType;
import com.serotonin.mango.util.ChangeComparable;
import com.serotonin.mango.util.ExportCodes;
import com.serotonin.mango.util.LocalizableJsonException;
import com.serotonin.mango.view.chart.ChartRenderer;
import com.serotonin.mango.view.text.BaseTextRenderer;
import com.serotonin.mango.view.text.NoneRenderer;
import com.serotonin.mango.view.text.PlainRenderer;
import com.serotonin.mango.view.text.TextRenderer;
import com.serotonin.mango.vo.event.PointEventDetectorVO;
import br.org.scadabr.util.ColorUtils;
import br.org.scadabr.util.SerializationHelper;
import br.org.scadabr.utils.TimePeriods;
import br.org.scadabr.vo.dataSource.PointLocatorVO;
import br.org.scadabr.web.dwr.DwrResponseI18n;
import br.org.scadabr.utils.i18n.LocalizableMessage;
import br.org.scadabr.vo.IntervalLoggingTypes;
import br.org.scadabr.vo.LoggingTypes;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;

@JsonRemoteEntity

public class DataPointVO implements Serializable, Cloneable, JsonSerializable, ChangeComparable<DataPointVO> {

    @Autowired //TODO use @Configurable for Validator
    private DataPointDao dataPointDao;

    private static final long serialVersionUID = -1;
    public static final String XID_PREFIX = "DP_";

    public DataType getDataType() {
        return pointLocator.getDataType();
    }


    public static final Set<TimePeriods> PURGE_TYPES = EnumSet.of(TimePeriods.DAYS, TimePeriods.WEEKS, TimePeriods.MONTHS, TimePeriods.YEARS);


    @Deprecated //TODO own units, not that of a subproject...
    public static final int ENGINEERING_UNITS_DEFAULT = 95; // No units
    private static final ExportCodes ENGINEERING_UNITS_CODES = new ExportCodes();

    static {
        for (int i = 0; i < 190; i++) {
            ENGINEERING_UNITS_CODES.addElement(i, new EngineeringUnits(i).toString().toUpperCase().replace(' ', '_'), "engUnit." + i);
        }
    }

    public LocalizableMessage getConfigurationDescription() {
        return pointLocator.getConfigurationDescription();
    }

    public boolean isNew() {
        return id == Common.NEW_ID;
    }

    //
    //
    // Properties
    //
    private int id = Common.NEW_ID;
    private String xid;
    @JsonRemoteProperty
    private String name;
    private int dataSourceId;
    @JsonRemoteProperty
    private String deviceName;
    @JsonRemoteProperty
    private boolean enabled;
    private int pointFolderId;
    private LoggingTypes loggingType = LoggingTypes.ON_CHANGE;
    private TimePeriods intervalLoggingPeriodType = TimePeriods.MINUTES;
    @JsonRemoteProperty
    private int intervalLoggingPeriod = 15;
    private IntervalLoggingTypes intervalLoggingType = IntervalLoggingTypes.INSTANT;
    @JsonRemoteProperty
    private double tolerance = 0;
    private TimePeriods _purgeType = TimePeriods.YEARS;
    @JsonRemoteProperty
    private int purgePeriod = 1;
    @JsonRemoteProperty(typeFactory = BaseTextRenderer.Factory.class)
    private TextRenderer textRenderer;
//TODO    @JsonRemoteProperty(typeFactory = BaseChartRenderer.Factory.class)
    private ChartRenderer chartRenderer;
    private List<PointEventDetectorVO> eventDetectors;
    private List<UserComment> comments;
    @JsonRemoteProperty
    private int defaultCacheSize = 1;
    @JsonRemoteProperty
    private boolean discardExtremeValues = false;
    @JsonRemoteProperty
    private double discardLowLimit = -Double.MAX_VALUE;
    @JsonRemoteProperty
    private double discardHighLimit = Double.MAX_VALUE;
    private int engineeringUnits = ENGINEERING_UNITS_DEFAULT;
    @JsonRemoteProperty
    private String chartColour;

    private PointLocatorVO pointLocator;

    //
    //
    // Convenience data from data source
    //
    private int dataSourceTypeId;
    private String dataSourceName;

    //
    //
    // Required for importing
    //
    @JsonRemoteProperty
    private String dataSourceXid;

    //
    //
    // Runtime data
    //
    /*
     * This is used by the watch list and graphic views to cache the last known value for a point to determine if the
     * browser side needs to be refreshed. Initially set to this value so that point views will update (since null
     * values in this case do in fact equal each other).
     */
    private PointValueTime lastValue = new PointValueTime((MangoValue) null, -1);

    public void resetLastValue() {
        lastValue = new PointValueTime((MangoValue) null, -1);
    }

    public PointValueTime lastValue() {
        return lastValue;
    }

    public void updateLastValue(PointValueTime pvt) {
        lastValue = pvt;
    }

    @Deprecated //TODO Make name with hirearchy path
    public String getExtendedName() {
        return deviceName + " - " + name;
    }

    public void defaultTextRenderer() {
        if (pointLocator == null) {
            textRenderer = new PlainRenderer("");
        } else {
            switch (pointLocator.getDataType()) {
                case IMAGE:
                    textRenderer = new NoneRenderer();
                    break;
                default:
                    textRenderer = new PlainRenderer("");
            }
        }
    }

    /*
     * This value is used by the watchlists. It is set when the watchlist is loaded to determine if the user is allowed
     * to set the point or not based upon various conditions.
     */
    private boolean settable;

    public boolean isSettable() {
        return settable;
    }

    public void setSettable(boolean settable) {
        this.settable = settable;
    }

    @Override
    public String getTypeKey() {
        return "event.audit.dataPoint";
    }

    @Override
    public void addProperties(List<LocalizableMessage> list) {
        AuditEventType.addPropertyMessage(list, "common.xid", xid);
        AuditEventType.addPropertyMessage(list, "dsEdit.points.name", name);
        AuditEventType.addPropertyMessage(list, "common.enabled", enabled);
        AuditEventType.addPropertyMessage(list, "pointEdit.logging.type", loggingType);
        AuditEventType.addPropertyMessage(list, "pointEdit.logging.period", intervalLoggingPeriodType.getPeriodDescription(intervalLoggingPeriod));
        AuditEventType.addPropertyMessage(list, "pointEdit.logging.valueType", intervalLoggingType);
        AuditEventType.addPropertyMessage(list, "pointEdit.logging.tolerance", tolerance);
        AuditEventType.addPropertyMessage(list, "pointEdit.logging.purge", _purgeType.getPeriodDescription(purgePeriod));
        AuditEventType.addPropertyMessage(list, "pointEdit.logging.defaultCache", defaultCacheSize);
        AuditEventType.addPropertyMessage(list, "pointEdit.logging.discard", discardExtremeValues);
        AuditEventType.addDoubleSientificProperty(list, "pointEdit.logging.discardLow", discardLowLimit);
        AuditEventType.addDoubleSientificProperty(list, "pointEdit.logging.discardHigh", discardHighLimit);
        AuditEventType.addPropertyMessage(list, "pointEdit.logging.engineeringUnits", engineeringUnits);
        AuditEventType.addPropertyMessage(list, "pointEdit.props.chartColour", chartColour);

        pointLocator.addProperties(list);
    }

    @Override
    public void addPropertyChanges(List<LocalizableMessage> list, DataPointVO from) {
        AuditEventType.maybeAddPropertyChangeMessage(list, "common.xid", from.xid, xid);
        AuditEventType.maybeAddPropertyChangeMessage(list, "dsEdit.points.name", from.name, name);
        AuditEventType.maybeAddPropertyChangeMessage(list, "common.enabled", from.enabled, enabled);
        AuditEventType.maybeAddPropertyChangeMessage(list, "pointEdit.logging.type", from.loggingType, loggingType);
        AuditEventType.maybeAddPropertyChangeMessage(list, "pointEdit.logging.period",
                from.intervalLoggingPeriodType.getPeriod(from.intervalLoggingPeriod),
                intervalLoggingPeriodType.getPeriod(intervalLoggingPeriod));
        AuditEventType.maybeAddPropertyChangeMessage(list, "pointEdit.logging.valueType", from.intervalLoggingType, intervalLoggingType);
        AuditEventType.maybeAddPropertyChangeMessage(list, "pointEdit.logging.tolerance", from.tolerance, tolerance);
        AuditEventType.maybeAddPropertyChangeMessage(list, "pointEdit.logging.purge", from._purgeType.getPeriodDescription(from.purgePeriod), _purgeType.getPeriodDescription(purgePeriod));
        AuditEventType.maybeAddPropertyChangeMessage(list, "pointEdit.logging.defaultCache", from.defaultCacheSize, defaultCacheSize);
        AuditEventType.maybeAddPropertyChangeMessage(list, "pointEdit.logging.discard", from.discardExtremeValues, discardExtremeValues);
        AuditEventType.evaluateDoubleScientific(list, textRenderer.getMessagePattern(), "pointEdit.logging.discardLow", from.discardLowLimit, discardLowLimit);
        AuditEventType.evaluateDoubleScientific(list, textRenderer.getMessagePattern(), "pointEdit.logging.discardHigh", from.discardHighLimit, discardHighLimit);
        AuditEventType.maybeAddPropertyChangeMessage(list, "pointEdit.logging.engineeringUnits", from.engineeringUnits, engineeringUnits);
        AuditEventType.maybeAddPropertyChangeMessage(list, "pointEdit.props.chartColour", from.chartColour, chartColour);

        pointLocator.addPropertyChanges(list, from.pointLocator);
    }

    public int getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(int dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPointFolderId() {
        return pointFolderId;
    }

    public void setPointFolderId(int pointFolderId) {
        this.pointFolderId = pointFolderId;
    }

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    public <T extends PointLocatorVO> T getPointLocator() {
        return (T) pointLocator;
    }

    public void setPointLocator(PointLocatorVO pointLocator) {
        this.pointLocator = pointLocator;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
        if (deviceName == null) {
            deviceName = dataSourceName;
        }
    }

    public String getDataSourceXid() {
        return dataSourceXid;
    }

    public void setDataSourceXid(String dataSourceXid) {
        this.dataSourceXid = dataSourceXid;
    }

    public int getDataSourceTypeId() {
        return dataSourceTypeId;
    }

    public void setDataSourceTypeId(int dataSourceTypeId) {
        this.dataSourceTypeId = dataSourceTypeId;
    }

    public LoggingTypes getLoggingType() {
        return loggingType;
    }

    public void setLoggingType(LoggingTypes loggingType) {
        this.loggingType = loggingType;
    }

    public int getPurgePeriod() {
        return purgePeriod;
    }

    public void setPurgePeriod(int purgePeriod) {
        this.purgePeriod = purgePeriod;
    }

    public TimePeriods getPurgeType() {
        return _purgeType;
    }

    public void setPurgeType(TimePeriods purgeType) {
        this._purgeType = purgeType;
    }

    public double getTolerance() {
        return tolerance;
    }

    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
    }

    //TODO use MessageFormat pattern for this ???
    public TextRenderer getTextRenderer() {
        return textRenderer;
    }

    public void setTextRenderer(TextRenderer textRenderer) {
        this.textRenderer = textRenderer;
    }

    public ChartRenderer getChartRenderer() {
        return chartRenderer;
    }

    public void setChartRenderer(ChartRenderer chartRenderer) {
        this.chartRenderer = chartRenderer;
    }

    public List<PointEventDetectorVO> getEventDetectors() {
        return eventDetectors;
    }

    public void setEventDetectors(List<PointEventDetectorVO> eventDetectors) {
        this.eventDetectors = eventDetectors;
    }

    public List<UserComment> getComments() {
        return comments;
    }

    public void setComments(List<UserComment> comments) {
        this.comments = comments;
    }

    public int getDefaultCacheSize() {
        return defaultCacheSize;
    }

    public void setDefaultCacheSize(int defaultCacheSize) {
        this.defaultCacheSize = defaultCacheSize;
    }

    public TimePeriods getIntervalLoggingPeriodType() {
        return intervalLoggingPeriodType;
    }

    public void setIntervalLoggingPeriodType(TimePeriods intervalLoggingPeriodType) {
        this.intervalLoggingPeriodType = intervalLoggingPeriodType;
    }

    public int getIntervalLoggingPeriod() {
        return intervalLoggingPeriod;
    }

    public void setIntervalLoggingPeriod(int intervalLoggingPeriod) {
        this.intervalLoggingPeriod = intervalLoggingPeriod;
    }

    public IntervalLoggingTypes getIntervalLoggingType() {
        return intervalLoggingType;
    }

    public void setIntervalLoggingType(IntervalLoggingTypes intervalLoggingType) {
        this.intervalLoggingType = intervalLoggingType;
    }

    public boolean isDiscardExtremeValues() {
        return discardExtremeValues;
    }

    public void setDiscardExtremeValues(boolean discardExtremeValues) {
        this.discardExtremeValues = discardExtremeValues;
    }

    public double getDiscardLowLimit() {
        return discardLowLimit;
    }

    public void setDiscardLowLimit(double discardLowLimit) {
        this.discardLowLimit = discardLowLimit;
    }

    public double getDiscardHighLimit() {
        return discardHighLimit;
    }

    public void setDiscardHighLimit(double discardHighLimit) {
        this.discardHighLimit = discardHighLimit;
    }

    public int getEngineeringUnits() {
        return engineeringUnits;
    }

    public void setEngineeringUnits(int engineeringUnits) {
        this.engineeringUnits = engineeringUnits;
    }

    public String getChartColour() {
        return chartColour;
    }

    public void setChartColour(String chartColour) {
        this.chartColour = chartColour;
    }

    public DataPointVO copy() {
        try {
            return (DataPointVO) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new ShouldNeverHappenException(e);
        }
    }

    @Override
    public String toString() {
        return "DataPointVO [id=" + id + ", xid=" + xid + ", name=" + name + ", dataSourceId=" + dataSourceId
                + ", deviceName=" + deviceName + ", enabled=" + enabled + ", pointFolderId=" + pointFolderId
                + ", loggingType=" + loggingType + ", intervalLoggingPeriodType=" + intervalLoggingPeriodType
                + ", intervalLoggingPeriod=" + intervalLoggingPeriod + ", intervalLoggingType=" + intervalLoggingType
                + ", tolerance=" + tolerance + ", purgeType=" + _purgeType + ", purgePeriod=" + purgePeriod
                + ", textRenderer=" + textRenderer + ", chartRenderer=" + chartRenderer + ", eventDetectors="
                + eventDetectors + ", comments=" + comments + ", defaultCacheSize=" + defaultCacheSize
                + ", discardExtremeValues=" + discardExtremeValues + ", discardLowLimit=" + discardLowLimit
                + ", discardHighLimit=" + discardHighLimit + ", engineeringUnits=" + engineeringUnits
                + ", chartColour=" + chartColour + ", pointLocator=" + pointLocator + ", dataSourceTypeId="
                + dataSourceTypeId + ", dataSourceName=" + dataSourceName + ", dataSourceXid=" + dataSourceXid
                + ", lastValue=" + lastValue + ", settable=" + settable + "]";
    }

    public void validate(DwrResponseI18n response) {
        if (xid.isEmpty()) {
            response.addContextual("xid", "validate.required");
        } else if (xid.length() > 50) {
            response.addContextual("xid", "validate.notLongerThan", 50);
        } else if (!dataPointDao.isXidUnique(xid, id)) {
            response.addContextual("xid", "validate.xidUsed");
        }

        if (name.isEmpty()) {
            response.addContextual("name", "validate.required");
        }

        if (loggingType == LoggingTypes.ON_CHANGE && getDataType() == DataType.NUMERIC) {
            if (tolerance < 0) {
                response.addContextual("tolerance", "validate.cannotBeNegative");
            }
        }

        if (intervalLoggingPeriod <= 0) {
            response.addContextual("intervalLoggingPeriod", "validate.greaterThanZero");
        }

        if (purgePeriod <= 0) {
            response.addContextual("purgePeriod", "validate.greaterThanZero");
        }

        if (textRenderer == null) {
            response.addContextual("textRenderer", "validate.required");
        }

        if (defaultCacheSize < 0) {
            response.addContextual("defaultCacheSize", "validate.cannotBeNegative");
        }

        if (discardExtremeValues && discardHighLimit <= discardLowLimit) {
            response.addContextual("discardHighLimit", "validate.greaterThanDiscardLow");
        }

        if (!chartColour.isEmpty()) {
            try {
                ColorUtils.toColor(chartColour);
            } catch (InvalidArgumentException e) {
                response.addContextual("chartColour", "validate.invalidValue");
            }
        }

        // Check text renderer type
        if (textRenderer != null && !textRenderer.getDef().supports(pointLocator.getDataType())) {
            response.addGeneric("validate.text.incompatible");
        }

        // Check chart renderer type
        if (chartRenderer != null && !chartRenderer.getType().supports(pointLocator.getDataType())) {
            response.addGeneric("validate.chart.incompatible");
        }
    }

    //
    //
    // Serialization
    //
    private static final int version = 8;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(version);
        SerializationHelper.writeSafeUTF(out, name);
        SerializationHelper.writeSafeUTF(out, deviceName);
        out.writeBoolean(enabled);
        out.writeInt(pointFolderId);
        out.writeInt(loggingType.mangoDbId);
        out.writeInt(intervalLoggingPeriodType.getId());
        out.writeInt(intervalLoggingPeriod);
        out.writeInt(intervalLoggingType.getId());
        out.writeDouble(tolerance);
        out.writeInt(_purgeType.getId());
        out.writeInt(purgePeriod);
        out.writeObject(textRenderer);
        out.writeObject(chartRenderer);
        out.writeObject(pointLocator);
        out.writeInt(defaultCacheSize);
        out.writeBoolean(discardExtremeValues);
        out.writeDouble(discardLowLimit);
        out.writeDouble(discardHighLimit);
        out.writeInt(engineeringUnits);
        SerializationHelper.writeSafeUTF(out, chartColour);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int ver = in.readInt();

        // Switch on the version of the class so that version changes can be elegantly handled.
        if (ver == 1) {
            name = SerializationHelper.readSafeUTF(in);
            deviceName = null;
            enabled = in.readBoolean();
            pointFolderId = 0;
            loggingType = LoggingTypes.fromMangoDbId(in.readInt());
            intervalLoggingPeriodType = TimePeriods.MINUTES;
            intervalLoggingPeriod = 15;
            intervalLoggingType = IntervalLoggingTypes.INSTANT;
            tolerance = in.readDouble();
            _purgeType = TimePeriods.fromId(in.readInt());
            purgePeriod = in.readInt();
            textRenderer = (TextRenderer) in.readObject();
            chartRenderer = (ChartRenderer) in.readObject();
            pointLocator = (PointLocatorVO) in.readObject();
            defaultCacheSize = 0;
            engineeringUnits = ENGINEERING_UNITS_DEFAULT;
            chartColour = null;
        } else if (ver == 2) {
            name = SerializationHelper.readSafeUTF(in);
            deviceName = null;
            enabled = in.readBoolean();
            pointFolderId = in.readInt();
            loggingType = LoggingTypes.fromMangoDbId(in.readInt());
            intervalLoggingPeriodType = TimePeriods.MINUTES;
            intervalLoggingPeriod = 15;
            intervalLoggingType = IntervalLoggingTypes.INSTANT;
            tolerance = in.readDouble();
            _purgeType = TimePeriods.fromId(in.readInt());
            purgePeriod = in.readInt();
            textRenderer = (TextRenderer) in.readObject();
            chartRenderer = (ChartRenderer) in.readObject();

            // The spinwave changes were not correctly implemented, so we need to handle potential errors here.
            try {
                pointLocator = (PointLocatorVO) in.readObject();
            } catch (IOException e) {
                // Turn this guy off.
                enabled = false;
            }
            defaultCacheSize = 0;
            engineeringUnits = ENGINEERING_UNITS_DEFAULT;
            chartColour = null;
        } else if (ver == 3) {
            name = SerializationHelper.readSafeUTF(in);
            deviceName = null;
            enabled = in.readBoolean();
            pointFolderId = in.readInt();
            loggingType = LoggingTypes.fromMangoDbId(in.readInt());
            intervalLoggingPeriodType = TimePeriods.MINUTES;
            intervalLoggingPeriod = 15;
            intervalLoggingType = IntervalLoggingTypes.INSTANT;
            tolerance = in.readDouble();
            _purgeType = TimePeriods.fromId(in.readInt());
            purgePeriod = in.readInt();
            textRenderer = (TextRenderer) in.readObject();
            chartRenderer = (ChartRenderer) in.readObject();

            // The spinwave changes were not correctly implemented, so we need to handle potential errors here.
            try {
                pointLocator = (PointLocatorVO) in.readObject();
            } catch (IOException e) {
                // Turn this guy off.
                enabled = false;
            }
            defaultCacheSize = in.readInt();
            engineeringUnits = ENGINEERING_UNITS_DEFAULT;
            chartColour = null;
        } else if (ver == 4) {
            name = SerializationHelper.readSafeUTF(in);
            deviceName = null;
            enabled = in.readBoolean();
            pointFolderId = in.readInt();
            loggingType = LoggingTypes.fromMangoDbId(in.readInt());
            intervalLoggingPeriodType = TimePeriods.fromId(in.readInt());
            intervalLoggingPeriod = in.readInt();
            intervalLoggingType = IntervalLoggingTypes.fromId(in.readInt());
            tolerance = in.readDouble();
            _purgeType = TimePeriods.fromId(in.readInt());
            purgePeriod = in.readInt();
            textRenderer = (TextRenderer) in.readObject();
            chartRenderer = (ChartRenderer) in.readObject();

            // The spinwave changes were not correctly implemented, so we need to handle potential errors here.
            try {
                pointLocator = (PointLocatorVO) in.readObject();
            } catch (IOException e) {
                // Turn this guy off.
                enabled = false;
            }
            defaultCacheSize = in.readInt();
            engineeringUnits = ENGINEERING_UNITS_DEFAULT;
            chartColour = null;
        } else if (ver == 5) {
            name = SerializationHelper.readSafeUTF(in);
            deviceName = null;
            enabled = in.readBoolean();
            pointFolderId = in.readInt();
            loggingType = LoggingTypes.fromMangoDbId(in.readInt());
            intervalLoggingPeriodType = TimePeriods.fromId(in.readInt());
            intervalLoggingPeriod = in.readInt();
            intervalLoggingType = IntervalLoggingTypes.fromId(in.readInt());
            tolerance = in.readDouble();
            _purgeType = TimePeriods.fromId(in.readInt());
            purgePeriod = in.readInt();
            textRenderer = (TextRenderer) in.readObject();
            chartRenderer = (ChartRenderer) in.readObject();
            pointLocator = (PointLocatorVO) in.readObject();
            defaultCacheSize = in.readInt();
            discardExtremeValues = in.readBoolean();
            discardLowLimit = in.readDouble();
            discardHighLimit = in.readDouble();
            engineeringUnits = ENGINEERING_UNITS_DEFAULT;
            chartColour = null;
        } else if (ver == 6) {
            name = SerializationHelper.readSafeUTF(in);
            deviceName = null;
            enabled = in.readBoolean();
            pointFolderId = in.readInt();
            loggingType = LoggingTypes.fromMangoDbId(in.readInt());
            intervalLoggingPeriodType = TimePeriods.fromId(in.readInt());
            intervalLoggingPeriod = in.readInt();
            intervalLoggingType = IntervalLoggingTypes.fromId(in.readInt());
            tolerance = in.readDouble();
            _purgeType = TimePeriods.fromId(in.readInt());
            purgePeriod = in.readInt();
            textRenderer = (TextRenderer) in.readObject();
            chartRenderer = (ChartRenderer) in.readObject();
            pointLocator = (PointLocatorVO) in.readObject();
            defaultCacheSize = in.readInt();
            discardExtremeValues = in.readBoolean();
            discardLowLimit = in.readDouble();
            discardHighLimit = in.readDouble();
            engineeringUnits = in.readInt();
            chartColour = null;
        } else if (ver == 7) {
            name = SerializationHelper.readSafeUTF(in);
            deviceName = null;
            enabled = in.readBoolean();
            pointFolderId = in.readInt();
            loggingType = LoggingTypes.fromMangoDbId(in.readInt());
            intervalLoggingPeriodType = TimePeriods.fromId(in.readInt());
            intervalLoggingPeriod = in.readInt();
            intervalLoggingType = IntervalLoggingTypes.fromId(in.readInt());
            tolerance = in.readDouble();
            _purgeType = TimePeriods.fromId(in.readInt());
            purgePeriod = in.readInt();
            textRenderer = (TextRenderer) in.readObject();
            chartRenderer = (ChartRenderer) in.readObject();
            pointLocator = (PointLocatorVO) in.readObject();
            defaultCacheSize = in.readInt();
            discardExtremeValues = in.readBoolean();
            discardLowLimit = in.readDouble();
            discardHighLimit = in.readDouble();
            engineeringUnits = in.readInt();
            chartColour = SerializationHelper.readSafeUTF(in);
        } else if (ver == 8) {
            name = SerializationHelper.readSafeUTF(in);
            deviceName = SerializationHelper.readSafeUTF(in);
            enabled = in.readBoolean();
            pointFolderId = in.readInt();
            loggingType = LoggingTypes.fromMangoDbId(in.readInt());
            intervalLoggingPeriodType = TimePeriods.fromId(in.readInt());
            intervalLoggingPeriod = in.readInt();
            intervalLoggingType = IntervalLoggingTypes.fromId(in.readInt());
            tolerance = in.readDouble();
            _purgeType = TimePeriods.fromId(in.readInt());
            purgePeriod = in.readInt();
            textRenderer = (TextRenderer) in.readObject();
            chartRenderer = (ChartRenderer) in.readObject();
            pointLocator = (PointLocatorVO) in.readObject();
            defaultCacheSize = in.readInt();
            discardExtremeValues = in.readBoolean();
            discardLowLimit = in.readDouble();
            discardHighLimit = in.readDouble();
            engineeringUnits = in.readInt();
            chartColour = SerializationHelper.readSafeUTF(in);
        }

        // Check the purge type. Weird how this could have been set to 0.
        if (_purgeType == null) {
            _purgeType = TimePeriods.YEARS;
        }
        // Ditto for purge period
        if (purgePeriod == 0) {
            purgePeriod = 1;
        }
    }

    @Override
    public void jsonSerialize(Map<String, Object> map) {
        map.put("xid", xid);
        map.put("loggingType", loggingType.name());
        map.put("intervalLoggingPeriodType", intervalLoggingPeriodType.name());
        map.put("intervalLoggingType", intervalLoggingType.name());
        map.put("purgeType", _purgeType.name());
        map.put("pointLocator", pointLocator);
        map.put("eventDetectors", eventDetectors);
        map.put("engineeringUnits", ENGINEERING_UNITS_CODES.getCode(engineeringUnits));
    }

    @Override
    public void jsonDeserialize(JsonReader reader, JsonObject json) throws JsonException {
        String text = json.getString("loggingType");
        if (text != null) {
            try {
            loggingType = LoggingTypes.valueOf(text);
            } catch (Exception e) {
                throw new LocalizableJsonException("emport.error.invalid", "loggingType", text,
                        loggingType.values());
            }
        }

        text = json.getString("intervalLoggingPeriodType");
        if (text != null) {
            try {
                intervalLoggingPeriodType = TimePeriods.valueOf(text);
            } catch (Exception e) {
                throw new LocalizableJsonException("emport.error.invalid", "intervalLoggingPeriodType", text,
                        TimePeriods.values());
            }
        }

        text = json.getString("intervalLoggingType");
        if (text != null) {
            try {
            intervalLoggingType = IntervalLoggingTypes.valueOf(text);
            } catch (Exception e) {
                throw new LocalizableJsonException("emport.error.invalid", "intervalLoggingType", text,
                        IntervalLoggingTypes.values());
            }
        }

        text = json.getString("purgeType");
        if (text != null) {
            try {
                _purgeType = TimePeriods.valueOf(text);
            } catch (Exception e) {
                throw new LocalizableJsonException("emport.error.invalid", "purgeType", text,
                        TimePeriods.values());
            }
        }

        JsonObject locatorJson = json.getJsonObject("pointLocator");
        if (locatorJson != null) {
            reader.populateObject(pointLocator, locatorJson);
        }

        JsonArray pedArray = json.getJsonArray("eventDetectors");
        if (pedArray != null) {
            for (JsonValue jv : pedArray.getElements()) {
                JsonObject pedObject = jv.toJsonObject();

                String pedXid = pedObject.getString("xid");
                if (pedXid.isEmpty()) {
                    throw new LocalizableJsonException("emport.error.ped.missingAttr", "xid");
                }

                // Use the ped xid to lookup an existing ped.
                PointEventDetectorVO ped = null;
                for (PointEventDetectorVO existing : eventDetectors) {
                    if (Objects.equals(pedXid, existing.getXid())) {
                        ped = existing;
                        break;
                    }
                }

                if (ped == null) {
                    // Create a new one
                    ped = new PointEventDetectorVO();
                    ped.setId(Common.NEW_ID);
                    ped.setXid(pedXid);
                    ped.njbSetDataPoint(this);
                    eventDetectors.add(ped);
                }

                reader.populateObject(ped, pedObject);
            }
        }

        text = json.getString("engineeringUnits");
        if (text != null) {
            engineeringUnits = ENGINEERING_UNITS_CODES.getId(text);
            if (engineeringUnits == -1) {
                engineeringUnits = ENGINEERING_UNITS_DEFAULT;
            }
        }
    }
}
