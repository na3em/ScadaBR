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
package com.serotonin.mango.vo.event;

import br.org.scadabr.ScadaBrConstants;
import java.util.List;
import br.org.scadabr.rt.event.type.EventSources;
import com.serotonin.mango.Common;
import com.serotonin.mango.db.dao.DataPointDao;
import com.serotonin.mango.rt.event.compound.CompoundEventDetectorRT;
import com.serotonin.mango.rt.event.compound.ConditionParseException;
import com.serotonin.mango.rt.event.compound.LogicalOperator;
import com.serotonin.mango.rt.event.type.AuditEventType;
import com.serotonin.mango.util.ChangeComparable;
import com.serotonin.mango.vo.DataPointVO;
import com.serotonin.mango.vo.User;
import com.serotonin.mango.vo.permission.Permissions;
import br.org.scadabr.vo.event.AlarmLevel;
import br.org.scadabr.web.dwr.DwrResponseI18n;
import br.org.scadabr.utils.i18n.LocalizableMessage;
import br.org.scadabr.utils.i18n.LocalizableMessageImpl;
import br.org.scadabr.vo.event.type.CompoundEventKey;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serotonin.mango.rt.event.type.CompoundDetectorEventType;
import org.junit.runner.Computer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * @author Matthew Lohbihler
 */
@Configurable
public class CompoundEventDetectorVO implements ChangeComparable<CompoundEventDetectorVO> {

    @Autowired
    private DataPointDao dataPointDao;

    public static final String XID_PREFIX = "CED_";

    private int id = ScadaBrConstants.NEW_ID;
    private String xid;

    private String name;
    private AlarmLevel alarmLevel = AlarmLevel.NONE;

    private boolean stateful = true;

    private boolean disabled = false;

    private String condition;
    private CompoundDetectorEventType compoundEventType;
    private CompoundEventKey compoundEventKey;

    public synchronized CompoundDetectorEventType getEventType() {
        if (compoundEventType == null) {
            compoundEventType = new CompoundDetectorEventType(this);
        }
        return compoundEventType;
    }

    @JsonIgnore
    public boolean isNew() {
        return id == ScadaBrConstants.NEW_ID;
    }

    @Override
    public String getTypeKey() {
        return "event.audit.compoundEventDetector";
    }

    public void validate(DwrResponseI18n response) {
        if (name.isEmpty()) {
            response.addContextual("name", "compoundDetectors.validation.nameRequired");
        }

        validate(condition, response);
    }

    public void validate(String condition, DwrResponseI18n response) {
        try {
            User user = Common.getUser();
            Permissions.ensureDataSourcePermission(user);

            LogicalOperator l = CompoundEventDetectorRT.parseConditionStatement(condition);
            List<String> keys = l.getDetectorKeys();

            // Get all of the point event detectors.
            List<DataPointVO> dataPoints = dataPointDao.getDataPoints(null, true);

            for (String key : keys) {
                if (!key.startsWith(EventDetectorVO.POINT_EVENT_DETECTOR_PREFIX)) {
                    continue;
                }

                boolean found = false;
                for (DataPointVO dp : dataPoints) {
                    if (!Permissions.hasDataSourcePermission(user, dp.getDataSourceId())) {
                        continue;
                    }

                    for (PointEventDetectorVO ped : dp.getEventDetectors()) {
                        if (ped.getEventDetectorKey().equals(key) && ped.isStateful()) {
                            found = true;
                            break;
                        }
                    }

                    if (found) {
                        break;
                    }
                }

                if (!found) {
                    throw new ConditionParseException("compoundDetectors.validation.invalidKey");
                }
            }
        } catch (ConditionParseException e) {
            response.addContextual("condition", e);
            if (e.isRange()) {
                response.addData("range", true);
                response.addData("from", e.getFrom());
                response.addData("to", e.getTo());
            }
        }
    }

    @Override
    public void addProperties(List<LocalizableMessage> list) {
        AuditEventType.addPropertyMessage(list, "common.xid", xid);
        AuditEventType.addPropertyMessage(list, "compoundDetectors.name", name);
        AuditEventType.addPropertyMessage(list, "common.alarmLevel", alarmLevel.getI18nKey());
        AuditEventType.addPropertyMessage(list, "common.rtn", stateful);
        AuditEventType.addPropertyMessage(list, "common.disabled", disabled);
        AuditEventType.addPropertyMessage(list, "compoundDetectors.condition", condition);
    }

    @Override
    public void addPropertyChanges(List<LocalizableMessage> list, CompoundEventDetectorVO from) {
        AuditEventType.maybeAddPropertyChangeMessage(list, "common.xid", from.xid, xid);
        AuditEventType.maybeAddPropertyChangeMessage(list, "compoundDetectors.name", from.name, name);
        AuditEventType.maybeAddPropertyChangeMessage(list, "common.alarmLevel", from.alarmLevel, alarmLevel);
        AuditEventType.maybeAddPropertyChangeMessage(list, "common.rtn", from.stateful, stateful);
        AuditEventType.maybeAddPropertyChangeMessage(list, "common.disabled", from.disabled, disabled);
        AuditEventType.maybeAddPropertyChangeMessage(list, "compoundDetectors.condition", from.condition, condition);
    }

    public CompoundEventDetectorRT createRuntime() {
        return new CompoundEventDetectorRT(this);
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

    public AlarmLevel getAlarmLevel() {
        return alarmLevel;
    }

    public void setAlarmLevel(AlarmLevel alarmLevel) {
        this.alarmLevel = alarmLevel;
    }

    public boolean isStateful() {
        return stateful;
    }

    public void setStateful(boolean stateful) {
        this.stateful = stateful;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    /**
     * @return the compoundEventKey
     */
    public CompoundEventKey getCompoundEventKey() {
        return compoundEventKey;
    }

    /**
     * @param compoundEventKey the compoundEventKey to set
     */
    public void setCompoundEventKey(CompoundEventKey compoundEventKey) {
        this.compoundEventKey = compoundEventKey;
    }

}
