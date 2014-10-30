package com.serotonin.mango.vo.event;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

import br.org.scadabr.ShouldNeverHappenException;
import br.org.scadabr.json.JsonException;
import br.org.scadabr.json.JsonObject;
import br.org.scadabr.json.JsonReader;
import br.org.scadabr.json.JsonRemoteEntity;
import br.org.scadabr.json.JsonRemoteProperty;
import br.org.scadabr.json.JsonSerializable;
import br.org.scadabr.rt.event.type.EventSources;
import br.org.scadabr.timer.cron.CronExpression;
import br.org.scadabr.timer.cron.CronParser;
import com.serotonin.mango.Common;
import com.serotonin.mango.db.dao.DataSourceDao;
import com.serotonin.mango.rt.event.maintenance.MaintenanceEventRT;
import com.serotonin.mango.rt.event.type.AuditEventType;
import com.serotonin.mango.rt.event.type.EventType;
import com.serotonin.mango.util.ChangeComparable;
import com.serotonin.mango.util.ExportCodes;
import com.serotonin.mango.util.LocalizableJsonException;
import com.serotonin.mango.vo.dataSource.DataSourceVO;
import br.org.scadabr.util.StringUtils;
import br.org.scadabr.vo.event.AlarmLevel;
import br.org.scadabr.web.dwr.DwrResponseI18n;
import br.org.scadabr.utils.i18n.LocalizableMessage;
import br.org.scadabr.utils.i18n.LocalizableMessageImpl;

@JsonRemoteEntity
public class MaintenanceEventVO implements ChangeComparable<MaintenanceEventVO>, JsonSerializable {

    public static final String XID_PREFIX = "ME_";

    public static final int TYPE_MANUAL = 1;
    public static final int TYPE_HOURLY = 2;
    public static final int TYPE_DAILY = 3;
    public static final int TYPE_WEEKLY = 4;
    public static final int TYPE_MONTHLY = 5;
    public static final int TYPE_YEARLY = 6;
    public static final int TYPE_ONCE = 7;
    public static final int TYPE_CRON = 8;

    public final static ExportCodes TYPE_CODES = new ExportCodes();

    static {
        TYPE_CODES.addElement(TYPE_MANUAL, "MANUAL", "maintenanceEvents.type.manual");
        TYPE_CODES.addElement(TYPE_HOURLY, "HOURLY", "maintenanceEvents.type.hour");
        TYPE_CODES.addElement(TYPE_DAILY, "DAILY", "maintenanceEvents.type.day");
        TYPE_CODES.addElement(TYPE_WEEKLY, "WEEKLY", "maintenanceEvents.type.week");
        TYPE_CODES.addElement(TYPE_MONTHLY, "MONTHLY", "maintenanceEvents.type.month");
        TYPE_CODES.addElement(TYPE_YEARLY, "YEARLY", "maintenanceEvents.type.year");
        TYPE_CODES.addElement(TYPE_ONCE, "ONCE", "maintenanceEvents.type.once");
        TYPE_CODES.addElement(TYPE_CRON, "CRON", "maintenanceEvents.type.cron");
    }

    private int id = Common.NEW_ID;
    private String xid;
    private int dataSourceId;
    @JsonRemoteProperty
    private String alias;
    private AlarmLevel alarmLevel = AlarmLevel.NONE;
    private int scheduleType = TYPE_MANUAL;
    @JsonRemoteProperty
    private boolean disabled = false;
    @JsonRemoteProperty
    private int activeYear;
    @JsonRemoteProperty
    private int activeMonth;
    @JsonRemoteProperty
    private int activeDay;
    @JsonRemoteProperty
    private int activeHour;
    @JsonRemoteProperty
    private int activeMinute;
    @JsonRemoteProperty
    private int activeSecond;
    @JsonRemoteProperty
    private String activeCron;
    @JsonRemoteProperty
    private int inactiveYear;
    @JsonRemoteProperty
    private int inactiveMonth;
    @JsonRemoteProperty
    private int inactiveDay;
    @JsonRemoteProperty
    private int inactiveHour;
    @JsonRemoteProperty
    private int inactiveMinute;
    @JsonRemoteProperty
    private int inactiveSecond;
    @JsonRemoteProperty
    private String inactiveCron;

    //
    //
    // Convenience data from data source
    //
    private int dataSourceTypeId;
    private String dataSourceName;
    private String dataSourceXid;

    public boolean isNew() {
        return id == Common.NEW_ID;
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

    public int getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(int dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public AlarmLevel getAlarmLevel() {
        return alarmLevel;
    }

    public void setAlarmLevel(AlarmLevel alarmLevel) {
        this.alarmLevel = alarmLevel;
    }

    public int getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(int scheduleType) {
        this.scheduleType = scheduleType;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public int getActiveYear() {
        return activeYear;
    }

    public void setActiveYear(int activeYear) {
        this.activeYear = activeYear;
    }

    public int getActiveMonth() {
        return activeMonth;
    }

    public void setActiveMonth(int activeMonth) {
        this.activeMonth = activeMonth;
    }

    public int getActiveDay() {
        return activeDay;
    }

    public void setActiveDay(int activeDay) {
        this.activeDay = activeDay;
    }

    public int getActiveHour() {
        return activeHour;
    }

    public void setActiveHour(int activeHour) {
        this.activeHour = activeHour;
    }

    public int getActiveMinute() {
        return activeMinute;
    }

    public void setActiveMinute(int activeMinute) {
        this.activeMinute = activeMinute;
    }

    public int getActiveSecond() {
        return activeSecond;
    }

    public void setActiveSecond(int activeSecond) {
        this.activeSecond = activeSecond;
    }

    public String getActiveCron() {
        return activeCron;
    }

    public void setActiveCron(String activeCron) {
        this.activeCron = activeCron;
    }

    public int getInactiveYear() {
        return inactiveYear;
    }

    public void setInactiveYear(int inactiveYear) {
        this.inactiveYear = inactiveYear;
    }

    public int getInactiveMonth() {
        return inactiveMonth;
    }

    public void setInactiveMonth(int inactiveMonth) {
        this.inactiveMonth = inactiveMonth;
    }

    public int getInactiveDay() {
        return inactiveDay;
    }

    public void setInactiveDay(int inactiveDay) {
        this.inactiveDay = inactiveDay;
    }

    public int getInactiveHour() {
        return inactiveHour;
    }

    public void setInactiveHour(int inactiveHour) {
        this.inactiveHour = inactiveHour;
    }

    public int getInactiveMinute() {
        return inactiveMinute;
    }

    public void setInactiveMinute(int inactiveMinute) {
        this.inactiveMinute = inactiveMinute;
    }

    public int getInactiveSecond() {
        return inactiveSecond;
    }

    public void setInactiveSecond(int inactiveSecond) {
        this.inactiveSecond = inactiveSecond;
    }

    public String getInactiveCron() {
        return inactiveCron;
    }

    public void setInactiveCron(String inactiveCron) {
        this.inactiveCron = inactiveCron;
    }

    public int getDataSourceTypeId() {
        return dataSourceTypeId;
    }

    public void setDataSourceTypeId(int dataSourceTypeId) {
        this.dataSourceTypeId = dataSourceTypeId;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public String getDataSourceXid() {
        return dataSourceXid;
    }

    public void setDataSourceXid(String dataSourceXid) {
        this.dataSourceXid = dataSourceXid;
    }

    public EventTypeVO getEventType() {
        return new EventTypeVO(EventSources.MAINTENANCE, id, 0, getDescription(), alarmLevel);
    }

    public LocalizableMessage getDescription() {
        if (alias != null) {
            return new LocalizableMessageImpl("common.default", alias);
        }
        switch (scheduleType) {
            case TYPE_MANUAL:
                return new LocalizableMessageImpl("maintenanceEvents.schedule.manual", dataSourceName);
            case TYPE_ONCE:
                return new LocalizableMessageImpl("maintenanceEvents.schedule.onceUntil", dataSourceName, new DateTime(activeYear, activeMonth, activeDay, activeHour, activeMinute, activeSecond, 0).toDate(),
                        new DateTime(inactiveYear, inactiveMonth, inactiveDay, inactiveHour, inactiveMinute, inactiveSecond, 0).toDate());
            case TYPE_HOURLY:
                String activeTime = StringUtils.pad(Integer.toString(activeMinute), '0', 2) + ":"
                        + StringUtils.pad(Integer.toString(activeSecond), '0', 2);
                return new LocalizableMessageImpl("maintenanceEvents.schedule.hoursUntil", dataSourceName, activeTime,
                        StringUtils.pad(Integer.toString(inactiveMinute), '0', 2) + ":"
                        + StringUtils.pad(Integer.toString(inactiveSecond), '0', 2));
            case TYPE_DAILY:
                return new LocalizableMessageImpl("maintenanceEvents.schedule.dailyUntil", dataSourceName, activeTime(), inactiveTime());
            case TYPE_WEEKLY:
                return new LocalizableMessageImpl("maintenanceEvents.schedule.weeklyUntil", dataSourceName, weekday(true), activeTime(), weekday(false), inactiveTime());
            case TYPE_MONTHLY:
                return new LocalizableMessageImpl("maintenanceEvents.schedule.monthlyUntil", dataSourceName, monthday(true), activeTime(), monthday(false), inactiveTime());
            case TYPE_YEARLY:
                return new LocalizableMessageImpl("maintenanceEvents.schedule.yearlyUntil", dataSourceName, monthday(true), month(true), activeTime(), monthday(false), month(false), inactiveTime());
            case TYPE_CRON:
                return new LocalizableMessageImpl("maintenanceEvents.schedule.cronUntil", dataSourceName, activeCron, inactiveCron);
            default:
                throw new ShouldNeverHappenException("Unknown schedule type: " + scheduleType);
        }
    }

    private LocalizableMessage getTypeMessage() {
        switch (scheduleType) {
            case TYPE_MANUAL:
                return new LocalizableMessageImpl("maintenanceEvents.type.manual");
            case TYPE_HOURLY:
                return new LocalizableMessageImpl("maintenanceEvents.type.hour");
            case TYPE_DAILY:
                return new LocalizableMessageImpl("maintenanceEvents.type.day");
            case TYPE_WEEKLY:
                return new LocalizableMessageImpl("maintenanceEvents.type.week");
            case TYPE_MONTHLY:
                return new LocalizableMessageImpl("maintenanceEvents.type.month");
            case TYPE_YEARLY:
                return new LocalizableMessageImpl("maintenanceEvents.type.year");
            case TYPE_ONCE:
                return new LocalizableMessageImpl("maintenanceEvents.type.once");
            case TYPE_CRON:
                return new LocalizableMessageImpl("maintenanceEvents.type.cron");
        }
        return null;
    }

    private String activeTime() {
        return StringUtils.pad(Integer.toString(activeHour), '0', 2) + ":"
                + StringUtils.pad(Integer.toString(activeMinute), '0', 2) + ":"
                + StringUtils.pad(Integer.toString(activeSecond), '0', 2);
    }

    private String inactiveTime() {
        return StringUtils.pad(Integer.toString(inactiveHour), '0', 2) + ":"
                + StringUtils.pad(Integer.toString(inactiveMinute), '0', 2) + ":"
                + StringUtils.pad(Integer.toString(inactiveSecond), '0', 2);
    }

    private static final String[] weekdays = {"", "common.day.mon", "common.day.tue", "common.day.wed",
        "common.day.thu", "common.day.fri", "common.day.sat", "common.day.sun"};

    private LocalizableMessage weekday(boolean active) {
        int day = activeDay;
        if (!active) {
            day = inactiveDay;
        }
        return new LocalizableMessageImpl(weekdays[day]);
    }

    private LocalizableMessage monthday(boolean active) {
        int day = activeDay;

        if (!active) {
            day = inactiveDay;
        }

        if (day == -3) {
            return new LocalizableMessageImpl("common.day.thirdLast");
        }
        if (day == -2) {
            return new LocalizableMessageImpl("common.day.secondLastLast");
        }
        if (day == -1) {
            return new LocalizableMessageImpl("common.day.last");
        }
        if (day != 11 && day % 10 == 1) {
            return new LocalizableMessageImpl("common.counting.st", Integer.toString(day));
        }
        if (day != 12 && day % 10 == 2) {
            return new LocalizableMessageImpl("common.counting.nd", Integer.toString(day));
        }
        if (day != 13 && day % 10 == 3) {
            return new LocalizableMessageImpl("common.counting.rd", Integer.toString(day));
        }
        return new LocalizableMessageImpl("common.counting.th", Integer.toString(day));
    }

    private static final String[] months = {"", "common.month.jan", "common.month.feb", "common.month.mar",
        "common.month.apr", "common.month.may", "common.month.jun", "common.month.jul", "common.month.aug",
        "common.month.sep", "common.month.oct", "common.month.nov", "common.month.dec"};

    private LocalizableMessage month(boolean active) {
        int day = activeDay;
        if (!active) {
            day = inactiveDay;
        }
        return new LocalizableMessageImpl(months[day]);
    }

    @Override
    public String getTypeKey() {
        return "event.audit.maintenanceEvent";
    }

    public void validate(DwrResponseI18n response) {
        if (alias.length() > 50) {
            response.addContextual("alias", "maintenanceEvents.validate.aliasTooLong");
        }

        if (dataSourceId <= 0) {
            response.addContextual("dataSourceId", "validate.invalidValue");
        }

        // Check that cron patterns are ok.
        if (scheduleType == TYPE_CRON) {
            try {
                new CronParser().parse(activeCron, CronExpression.TIMEZONE_UTC);
            } catch (Exception e) {
                response.addContextual("activeCron", "maintenanceEvents.validate.activeCron", e);
            }

            try {
                new CronParser().parse(inactiveCron, CronExpression.TIMEZONE_UTC);
            } catch (Exception e) {
                response.addContextual("inactiveCron", "maintenanceEvents.validate.inactiveCron", e);
            }
        }

        // Test that the triggers can be created.
        MaintenanceEventRT rt = new MaintenanceEventRT(this);
        try {
            rt.createTrigger(true);
        } catch (RuntimeException e) {
            response.addContextual("activeCron", "maintenanceEvents.validate.activeTrigger", e);
        }

        try {
            rt.createTrigger(false);
        } catch (RuntimeException e) {
            response.addContextual("inactiveCron", "maintenanceEvents.validate.inactiveTrigger", e);
        }

        // If the event is once, make sure the active time is earlier than the inactive time.
        if (scheduleType == TYPE_ONCE) {
            DateTime adt = new DateTime(activeYear, activeMonth, activeDay, activeHour, activeMinute, activeSecond, 0);
            DateTime idt = new DateTime(inactiveYear, inactiveMonth, inactiveDay, inactiveHour, inactiveMinute,
                    inactiveSecond, 0);
            if (idt.getMillis() <= adt.getMillis()) {
                response.addContextual("scheduleType", "maintenanceEvents.validate.invalidRtn");
            }
        }
    }

    @Override
    public void addProperties(List<LocalizableMessage> list) {
        AuditEventType.addPropertyMessage(list, "common.xid", xid);
        AuditEventType.addPropertyMessage(list, "maintenanceEvents.dataSource", dataSourceId);
        AuditEventType.addPropertyMessage(list, "maintenanceEvents.alias", alias);
        AuditEventType.addPropertyMessage(list, "common.alarmLevel", alarmLevel.getI18nKey());
        AuditEventType.addPropertyMessage(list, "maintenanceEvents.type", getTypeMessage());
        AuditEventType.addPropertyMessage(list, "common.disabled", disabled);
        AuditEventType.addPropertyMessage(list, "common.configuration", getDescription());
    }

    @Override
    public void addPropertyChanges(List<LocalizableMessage> list, MaintenanceEventVO from) {
        AuditEventType.maybeAddPropertyChangeMessage(list, "common.xid", from.xid, xid);
        AuditEventType.maybeAddPropertyChangeMessage(list, "maintenanceEvents.dataSource", from.dataSourceId,
                dataSourceId);
        AuditEventType.maybeAddPropertyChangeMessage(list, "maintenanceEvents.alias", from.alias, alias);
        AuditEventType.maybeAddPropertyChangeMessage(list, "common.alarmLevel", from.alarmLevel, alarmLevel);
        if (from.scheduleType != scheduleType) {
            AuditEventType.addPropertyChangeMessage(list, "maintenanceEvents.type", from.getTypeMessage(),
                    getTypeMessage());
        }
        AuditEventType.maybeAddPropertyChangeMessage(list, "common.disabled", from.disabled, disabled);
        if (from.activeYear != activeYear || from.activeMonth != activeMonth || from.activeDay != activeDay
                || from.activeHour != activeHour || from.activeMinute != activeMinute
                || from.activeSecond != activeSecond || (from.activeCron == null ? activeCron != null : !from.activeCron.equals(activeCron))
                || from.inactiveYear != inactiveYear || from.inactiveMonth != inactiveMonth
                || from.inactiveDay != inactiveDay || from.inactiveHour != inactiveHour
                || from.inactiveMinute != inactiveMinute || from.inactiveSecond != inactiveSecond
                || (from.inactiveCron == null ? inactiveCron != null : !from.inactiveCron.equals(inactiveCron))) {
            AuditEventType.maybeAddPropertyChangeMessage(list, "common.configuration", from.getDescription(),
                    getDescription());
        }
    }

    //
    //
    // Serialization
    //
    @Override
    public void jsonSerialize(Map<String, Object> map) {
        map.put("xid", xid);
        map.put("dataSourceXid", dataSourceXid);
        map.put("alarmLevel", alarmLevel.getName());
        map.put("scheduleType", TYPE_CODES.getCode(scheduleType));
    }

    @Override
    public void jsonDeserialize(JsonReader reader, JsonObject json) throws JsonException {
        String text = json.getString("dataSourceXid");
        if (text != null) {
            DataSourceVO<?> ds = DataSourceDao.getInstance().getDataSource(text);
            if (ds == null) {
                throw new LocalizableJsonException("emport.error.maintenanceEvent.invalid", "dataSourceXid", text);
            }
            dataSourceId = ds.getId();
        }

        text = json.getString("alarmLevel");
        if (text != null) {
            try {
                alarmLevel = AlarmLevel.valueOf(text);
            } catch (Exception e) {
                throw new LocalizableJsonException("emport.error.maintenanceEvent.invalid", "alarmLevel", text,
                        AlarmLevel.nameValues());
            }
        }

        text = json.getString("scheduleType");
        if (text != null) {
            scheduleType = TYPE_CODES.getId(text);
            if (!TYPE_CODES.isValidId(scheduleType)) {
                throw new LocalizableJsonException("emport.error.maintenanceEvent.invalid", "scheduleType", text,
                        TYPE_CODES.getCodeList());
            }
        }
    }
}
