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
package com.serotonin.mango.vo.mailingList;

import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;

public class AddressEntry extends EmailRecipient {

    private String address;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public void appendAddresses(Set<String> addresses, DateTime sendTime) {
        appendAllAddresses(addresses);
    }

    @Override
    public void appendAllAddresses(Set<String> addresses) {
        addresses.add(address);
    }

    @Override
    public int getRecipientType() {
        return EmailRecipient.TYPE_ADDRESS;
    }

    @Override
    public int getReferenceId() {
        return 0;
    }

    @Override
    public String getReferenceAddress() {
        return address;
    }

    @Override
    public String toString() {
        return address;
    }

}
