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
package com.serotonin.mango.db.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.simple.ParameterizedRowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.mango.Common;
import com.serotonin.mango.vo.mailingList.AddressEntry;
import com.serotonin.mango.vo.mailingList.EmailRecipient;
import com.serotonin.mango.vo.mailingList.MailingList;
import com.serotonin.mango.vo.mailingList.UserEntry;
import com.serotonin.mango.web.dwr.beans.RecipientListEntryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Matthew Lohbihler
 */
@Service
public class MailingListDao extends BaseDao {

    @Autowired
    private UserDao userDao;

    public String generateUniqueXid() {
        return generateUniqueXid(MailingList.XID_PREFIX, "mailingLists");
    }

    public boolean isXidUnique(String xid, int excludeId) {
        return isXidUnique(xid, excludeId, "mailingLists");
    }
    private static final String MAILING_LIST_SELECT = "select id, xid, name from mailingLists ";

    public List<MailingList> getMailingLists() {
        List<MailingList> result = getSimpleJdbcTemplate().query(MAILING_LIST_SELECT + "order by name", new MailingListRowMapper());
        setRelationalData(result);
        return result;
    }

    public MailingList getMailingList(int id) {
        MailingList ml = getSimpleJdbcTemplate().queryForObject(MAILING_LIST_SELECT + "where id=?", new MailingListRowMapper(), id);
        setRelationalData(ml);
        return ml;
    }

    public MailingList getMailingList(String xid) {
        MailingList ml = getSimpleJdbcTemplate().queryForObject(MAILING_LIST_SELECT + "where xid=?", new MailingListRowMapper(), xid);
        if (ml != null) {
            setRelationalData(ml);
        }
        return ml;
    }

    class MailingListRowMapper implements ParameterizedRowMapper<MailingList> {

        @Override
        public MailingList mapRow(ResultSet rs, int rowNum) throws SQLException {
            MailingList ml = new MailingList();
            ml.setId(rs.getInt(1));
            ml.setXid(rs.getString(2));
            ml.setName(rs.getString(3));
            return ml;
        }
    }

    private void setRelationalData(List<MailingList> mls) {
        for (MailingList ml : mls) {
            setRelationalData(ml);
        }
    }
    private static final String MAILING_LIST_INACTIVE_SELECT = "select inactiveInterval from mailingListInactive where mailingListId=?";
    private static final String MAILING_LIST_ENTRIES_SELECT = "select typeId, userId, address, '' from mailingListMembers where mailingListId=?";

    private void setRelationalData(MailingList ml) {
        ml.getInactiveIntervals().addAll(
                getSimpleJdbcTemplate().query(MAILING_LIST_INACTIVE_SELECT, new MailingListScheduleInactiveMapper(), ml.getId()));

        ml.setEntries(getSimpleJdbcTemplate().query(MAILING_LIST_ENTRIES_SELECT, new EmailRecipientRowMapper(), ml.getId()));

        // Update the user type entries with their respective user objects.
        populateEntrySubclasses(ml.getEntries());
    }

    class MailingListScheduleInactiveMapper implements ParameterizedRowMapper<Integer> {

        @Override
        public Integer mapRow(ResultSet rs, int rowNum) throws SQLException {
            return rs.getInt(1);
        }
    }

    class EmailRecipientRowMapper implements ParameterizedRowMapper<EmailRecipient> {

        @Override
        public EmailRecipient mapRow(ResultSet rs, int rowNum) throws SQLException {
            int type = rs.getInt(1);
            switch (type) {
                case EmailRecipient.TYPE_MAILING_LIST:
                    MailingList ml = new MailingList();
                    ml.setId(rs.getInt(2));
                    ml.setName(rs.getString(4));
                    return ml;
                case EmailRecipient.TYPE_USER:
                    UserEntry ue = new UserEntry();
                    ue.setUserId(rs.getInt(2));
                    return ue;
                case EmailRecipient.TYPE_ADDRESS:
                    AddressEntry ae = new AddressEntry();
                    ae.setAddress(rs.getString(3));
                    return ae;
            }
            throw new ShouldNeverHappenException("Unknown mailing list entry type: " + type);
        }
    }

    public Set<String> getRecipientAddresses(List<RecipientListEntryBean> beans, DateTime sendTime) {
        List<EmailRecipient> entries = new ArrayList<EmailRecipient>(beans.size());
        for (RecipientListEntryBean bean : beans) {
            entries.add(bean.createEmailRecipient());
        }
        populateEntrySubclasses(entries);
        Set<String> addresses = new HashSet<String>();
        for (EmailRecipient entry : entries) {
            entry.appendAddresses(addresses, sendTime);
        }
        return addresses;
    }

    public void populateEntrySubclasses(List<EmailRecipient> entries) {
        // Update the user type entries with their respective user objects.
        for (EmailRecipient e : entries) {
            if (e instanceof MailingList) // NOTE: this does not set the mailing list name.
            {
                setRelationalData((MailingList) e);
            } else if (e instanceof UserEntry) {
                UserEntry ue = (UserEntry) e;
                ue.setUser(userDao.getUser(ue.getUserId()));
            }
        }
    }
    private static final String MAILING_LIST_UPDATE = "update mailingLists set xid=?, name=? where id=?";

    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public void saveMailingList(final MailingList ml) {
        if (ml.getId() == Common.NEW_ID) {
            SimpleJdbcInsert insertActor = new SimpleJdbcInsert(getDataSource()).withTableName("pointHierarchy").usingGeneratedKeyColumns("id");
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("xid", ml.getXid());
            params.put("name", ml.getName());

            Number id = insertActor.executeAndReturnKey(params);
            ml.setId(id.intValue());
        } else {
            getSimpleJdbcTemplate().update(MAILING_LIST_UPDATE, ml.getXid(), ml.getName(), ml.getId());
        }
        saveRelationalData(ml);
    }
    private static final String MAILING_LIST_INACTIVE_INSERT = "insert into mailingListInactive (mailingListId, inactiveInterval) values (?,?)";
    private static final String MAILING_LIST_ENTRY_INSERT = "insert into mailingListMembers (mailingListId, typeId, userId, address) values (?,?,?,?)";

    void saveRelationalData(final MailingList ml) {
        // Save the inactive intervals.
        getSimpleJdbcTemplate().update("delete from mailingListInactive where mailingListId=?", ml.getId());

        // Save what is in the mailing list object.
        final List<Integer> intervalIds = new ArrayList<Integer>(ml.getInactiveIntervals());
        getJdbcTemplate().batchUpdate(MAILING_LIST_INACTIVE_INSERT, new BatchPreparedStatementSetter() {

            @Override
            public int getBatchSize() {
                return intervalIds.size();
            }

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setInt(1, ml.getId());
                ps.setInt(2, intervalIds.get(i));
            }
        });

        // Delete existing entries
        getSimpleJdbcTemplate().update("delete from mailingListMembers where mailingListId=?", ml.getId());

        // Save what is in the mailing list object.
        final List<EmailRecipient> entries = ml.getEntries();
        getJdbcTemplate().batchUpdate(MAILING_LIST_ENTRY_INSERT, new BatchPreparedStatementSetter() {

            @Override
            public int getBatchSize() {
                return entries.size();
            }

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                EmailRecipient e = entries.get(i);
                ps.setInt(1, ml.getId());
                ps.setInt(2, e.getRecipientType());
                ps.setInt(3, e.getReferenceId());
                ps.setString(4, e.getReferenceAddress());
            }
        });

    }

    public void deleteMailingList(final int mailingListId) {
        getSimpleJdbcTemplate().update("delete from mailingLists where id=?", mailingListId);
    }
}
