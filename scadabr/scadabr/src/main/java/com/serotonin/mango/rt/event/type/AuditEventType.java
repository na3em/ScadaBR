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
package com.serotonin.mango.rt.event.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.org.scadabr.json.JsonException;
import br.org.scadabr.json.JsonObject;
import br.org.scadabr.json.JsonReader;
import br.org.scadabr.json.JsonRemoteEntity;
import br.org.scadabr.rt.event.type.DuplicateHandling;
import br.org.scadabr.rt.event.type.EventSources;
import br.org.scadabr.utils.ImplementMeException;
import br.org.scadabr.vo.event.AlarmLevel;
import com.serotonin.mango.Common;
import com.serotonin.mango.db.dao.SystemSettingsDao;
import com.serotonin.mango.util.ChangeComparable;
import com.serotonin.mango.util.ExportCodes;
import com.serotonin.mango.vo.User;
import com.serotonin.mango.vo.event.EventTypeVO;
import br.org.scadabr.web.i18n.LocalizableI18nKey;
import br.org.scadabr.utils.i18n.LocalizableMessage;
import br.org.scadabr.utils.i18n.LocalizableMessageImpl;
import java.util.Objects;

@JsonRemoteEntity
public class AuditEventType extends EventType {

    //
    // /
    // / Static stuff
    // /
    //
    private static final String AUDIT_SETTINGS_PREFIX = "auditEventAlarmLevel";

    public static final int TYPE_DATA_SOURCE = 1;
    public static final int TYPE_DATA_POINT = 2;
    public static final int TYPE_POINT_EVENT_DETECTOR = 3;
    public static final int TYPE_COMPOUND_EVENT_DETECTOR = 4;
    public static final int TYPE_SCHEDULED_EVENT = 5;
    public static final int TYPE_EVENT_HANDLER = 6;
    public static final int TYPE_POINT_LINK = 7;
    public static final int TYPE_MAINTENANCE_EVENT = 8;

    private static final ExportCodes TYPE_CODES = new ExportCodes();

    static {
        TYPE_CODES.addElement(TYPE_DATA_SOURCE, "DATA_SOURCE");
        TYPE_CODES.addElement(TYPE_DATA_POINT, "DATA_POINT");
        TYPE_CODES.addElement(TYPE_POINT_EVENT_DETECTOR, "POINT_EVENT_DETECTOR");
        TYPE_CODES.addElement(TYPE_COMPOUND_EVENT_DETECTOR, "COMPOUND_EVENT_DETECTOR");
        TYPE_CODES.addElement(TYPE_SCHEDULED_EVENT, "SCHEDULED_EVENT");
        TYPE_CODES.addElement(TYPE_EVENT_HANDLER, "EVENT_HANDLER");
        TYPE_CODES.addElement(TYPE_POINT_LINK, "POINT_LINK");
        TYPE_CODES.addElement(TYPE_MAINTENANCE_EVENT, "MAINTENANCE_EVENT");
    }

    private static List<EventTypeVO> auditEventTypes;

    public static List<EventTypeVO> getAuditEventTypes() {
        if (auditEventTypes == null) {
            auditEventTypes = new ArrayList<>();

            addEventTypeVO(TYPE_DATA_SOURCE, "event.audit.dataSource");
            addEventTypeVO(TYPE_DATA_POINT, "event.audit.dataPoint");
            addEventTypeVO(TYPE_POINT_EVENT_DETECTOR, "event.audit.pointEventDetector");
            addEventTypeVO(TYPE_COMPOUND_EVENT_DETECTOR, "event.audit.compoundEventDetector");
            addEventTypeVO(TYPE_SCHEDULED_EVENT, "event.audit.scheduledEvent");
            addEventTypeVO(TYPE_EVENT_HANDLER, "event.audit.eventHandler");
            addEventTypeVO(TYPE_POINT_LINK, "event.audit.pointLink");
            addEventTypeVO(TYPE_MAINTENANCE_EVENT, "event.audit.maintenanceEvent");
        }
        return auditEventTypes;
    }

    private static void addEventTypeVO(int type, String key) {
        auditEventTypes.add(new EventTypeVO(EventSources.AUDIT, type, 0, new LocalizableMessageImpl(key),
                SystemSettingsDao.getAlarmLevel(AUDIT_SETTINGS_PREFIX + type, AlarmLevel.INFORMATION)));
    }

    public static EventTypeVO getEventType(int type) {
        for (EventTypeVO et : getAuditEventTypes()) {
            if (et.getTypeRef1() == type) {
                return et;
            }
        }
        return null;
    }

    public static void setEventTypeAlarmLevel(int type, AlarmLevel alarmLevel) {
        EventTypeVO et = getEventType(type);
        et.setAlarmLevel(alarmLevel);

        SystemSettingsDao dao = SystemSettingsDao.getInstance();
        dao.setAlarmLevel(AUDIT_SETTINGS_PREFIX + type, alarmLevel);
    }

    public static void raiseAddedEvent(int auditEventTypeId, ChangeComparable<?> o) {
        List<LocalizableMessage> list = new ArrayList<>();
        o.addProperties(list);
        raiseEvent(auditEventTypeId, o, "event.audit.added", list.toArray());
    }

    public static <T> void raiseChangedEvent(int auditEventTypeId, T from, ChangeComparable<T> to) {
        List<LocalizableMessage> changes = new ArrayList<>();
        to.addPropertyChanges(changes, from);
        if (changes.isEmpty()) // If the object wasn't in fact changed, don't raise an event.
        {
            return;
        }
        raiseEvent(auditEventTypeId, to, "event.audit.changed", changes.toArray());
    }

    public static void raiseDeletedEvent(int auditEventTypeId, ChangeComparable<?> o) {
        List<LocalizableMessage> list = new ArrayList<>();
        o.addProperties(list);
        raiseEvent(auditEventTypeId, o, "event.audit.deleted", list.toArray());
    }

    private static void raiseEvent(int auditEventTypeId, ChangeComparable<?> o, String key, Object[] props) {
        User user = Common.getUser();
        Object username;
        if (user != null) {
            username = user.getUsername() + " (" + user.getId() + ")";
        } else {
            String descKey = Common.getBackgroundProcessDescription();
            if (descKey == null) {
                username = new LocalizableMessageImpl("common.unknown");
            } else {
                username = new LocalizableMessageImpl(descKey);
            }
        }

        LocalizableMessage message = new LocalizableMessageImpl(key, username, new LocalizableMessageImpl(o.getTypeKey()),
                o.getId(), new LocalizableMessageImpl("event.audit.propertyList." + props.length, props));

        AuditEventType type = new AuditEventType(auditEventTypeId, o.getId());
        type.setRaisingUser(user);

        Common.ctx.getEventManager().raiseEvent(type, System.currentTimeMillis(), false,
                getEventType(type.getAuditEventTypeId()).getAlarmLevel(), message, null);
    }

    //
    // /
    // / Utility methods for other classes
    // /
    //
    public static void addPropertyMessage(List<LocalizableMessage> list, String propertyNameKey, Object propertyValue) {
        list.add(new LocalizableMessageImpl("event.audit.property", new LocalizableMessageImpl(propertyNameKey), propertyValue));
    }

    public static void addDoubleSientificProperty(List<LocalizableMessage> list, String propertyNameKey, double propertyValue) {
        list.add(new LocalizableMessageImpl("event.audit.propertyDoubleScientific", new LocalizableMessageImpl(propertyNameKey), propertyValue));
    }

    public static void addPropertyMessage(List<LocalizableMessage> list, String propertyNameKey, boolean propertyValue) {
        list.add(new LocalizableMessageImpl("event.audit.property", new LocalizableMessageImpl(propertyNameKey),
                getBooleanMessage(propertyValue)));
    }

    public static void addExportCodeMessage(List<LocalizableMessage> list, String propertyNameKey, ExportCodes codes,
            int id) {
        list.add(new LocalizableMessageImpl("event.audit.property", new LocalizableMessageImpl(propertyNameKey),
                getExportCodeMessage(codes, id)));
    }

    public static void maybeAddPropertyChangeMessage(List<LocalizableMessage> list, String propertyNameKey,
            int fromValue, int toValue) {
        if (fromValue != toValue) {
            addPropertyChangeMessage(list, propertyNameKey, fromValue, toValue);
        }
    }

    public static void maybeAddPropertyChangeMessage(List<LocalizableMessage> list, String propertyNameKey,
            LocalizableI18nKey fromValue, LocalizableI18nKey toValue) {
        if (!Objects.equals(fromValue, toValue)) {
            addPropertyChangeMessage(list, propertyNameKey, fromValue, toValue);
        }
    }

    public static void maybeAddPropertyChangeMessage(List<LocalizableMessage> list, String propertyNameKey,
            Object fromValue, Object toValue) {
        if (!Objects.equals(fromValue, toValue)) {
            addPropertyChangeMessage(list, propertyNameKey, fromValue, toValue);
        }
    }

    @Deprecated // Use more specific
    public static void maybeAddPropertyChangeMessage(List<LocalizableMessage> list, String propertyNameKey,
            boolean fromValue, boolean toValue) {
        if (fromValue != toValue) {
            addPropertyChangeMessage(list, propertyNameKey, getBooleanMessage(fromValue), getBooleanMessage(toValue));
        }
    }

    public static void evaluateDoubleScientific(List<LocalizableMessage> list, String propertyNameKey, String formatPattern, double fromValue, double toValue) {
//TODO user MessageFormatPattern
        if (fromValue != toValue) {
            list.add(new LocalizableMessageImpl("event.audit.changedPropertyDoubleScientific", propertyNameKey, fromValue, toValue));
        }
throw new ImplementMeException(); //"MessageFormat");
    }

    public static void maybeAddExportCodeChangeMessage(List<LocalizableMessage> list, String propertyNameKey,
            ExportCodes exportCodes, int fromId, int toId) {
        if (fromId != toId) {
            addPropertyChangeMessage(list, propertyNameKey, getExportCodeMessage(exportCodes, fromId),
                    getExportCodeMessage(exportCodes, toId));
        }
    }

    private static LocalizableMessage getBooleanMessage(boolean value) {
        if (value) {
            return new LocalizableMessageImpl("common.true");
        }
        return new LocalizableMessageImpl("common.false");
    }

    private static LocalizableMessage getExportCodeMessage(ExportCodes exportCodes, int id) {
        String key = exportCodes.getKey(id);
        if (key == null) {
            return new LocalizableMessageImpl("common.unknown");
        }
        return new LocalizableMessageImpl(key);
    }

    public static void addPropertyChangeMessage(List<LocalizableMessage> list, String propertyNameKey, Object fromValue, Object toValue) {
        list.add(new LocalizableMessageImpl("event.audit.changedProperty", new LocalizableMessageImpl(propertyNameKey), fromValue, toValue));
    }

    //
    // /
    // / Instance stuff
    // /
    //
    private int auditEventTypeId;
    private int referenceId;
    private User raisingUser;

    public AuditEventType() {
        // Required for reflection.
    }

    public AuditEventType(int auditEventTypeId, int referenceId) {
        this.auditEventTypeId = auditEventTypeId;
        this.referenceId = referenceId;
    }

    @Override
    public EventSources getEventSource() {
        return EventSources.AUDIT;
    }

    public int getAuditEventTypeId() {
        return auditEventTypeId;
    }

    @Override
    public String toString() {
        return "AuditEventType(auditTypeId=" + auditEventTypeId + ", referenceId=" + referenceId + ")";
    }

    @Override
    public DuplicateHandling getDuplicateHandling() {
        return DuplicateHandling.ALLOW;
    }

    @Override
    public int getReferenceId1() {
        return auditEventTypeId;
    }

    @Override
    public int getReferenceId2() {
        return referenceId;
    }

    public void setRaisingUser(User raisingUser) {
        this.raisingUser = raisingUser;
    }

    @Override
    public boolean excludeUser(User user) {
        if (raisingUser != null && !raisingUser.isReceiveOwnAuditEvents()) {
            return user.equals(raisingUser);
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + auditEventTypeId;
        result = prime * result + referenceId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AuditEventType other = (AuditEventType) obj;
        if (auditEventTypeId != other.auditEventTypeId) {
            return false;
        }
        if (referenceId != other.referenceId) {
            return false;
        }
        return true;
    }

    //
    // /
    // / Serialization
    // /
    //
    @Override
    public void jsonSerialize(Map<String, Object> map) {
        super.jsonSerialize(map);
        map.put("auditType", TYPE_CODES.getCode(auditEventTypeId));
    }

    @Override
    public void jsonDeserialize(JsonReader reader, JsonObject json) throws JsonException {
        super.jsonDeserialize(reader, json);
        auditEventTypeId = getInt(json, "auditType", TYPE_CODES);
    }
}
