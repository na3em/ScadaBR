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
package com.serotonin.mango.view.chart;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;


import br.org.scadabr.utils.TimePeriods;
import com.serotonin.mango.vo.DataPointVO;


public class ImageChartRenderer extends TimePeriodChartRenderer {

    @Override
    public ChartType getType() {
        return ChartType.IMAGE;
    }

    public ImageChartRenderer() {
        // no op
    }

    public ImageChartRenderer(TimePeriods timePeriod, int numberOfPeriods) {
        super(timePeriod, numberOfPeriods);
    }

    @Override
    public void addDataToModel(Map<String, Object> model, DataPointVO point) {
        // Nothing to do.
    }

    //
    // /
    // / Serialization
    // /
    //
    private static final long serialVersionUID = -1;
    private static final int version = 1;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(version);
    }

    private void readObject(ObjectInputStream in) throws IOException {
        in.readInt();
    }
}
