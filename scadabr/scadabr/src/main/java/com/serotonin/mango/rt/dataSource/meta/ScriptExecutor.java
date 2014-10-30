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
package com.serotonin.mango.rt.dataSource.meta;

import br.org.scadabr.DataType;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import br.org.scadabr.ShouldNeverHappenException;
import br.org.scadabr.db.IntValuePair;
import br.org.scadabr.io.StreamUtils;
import br.org.scadabr.utils.TimePeriods;
import com.serotonin.mango.Common;
import com.serotonin.mango.rt.RuntimeManager;
import com.serotonin.mango.rt.dataImage.DataPointRT;
import com.serotonin.mango.rt.dataImage.IDataPoint;
import com.serotonin.mango.rt.dataImage.PointValueTime;
import com.serotonin.mango.rt.dataImage.types.AlphanumericValue;
import com.serotonin.mango.rt.dataImage.types.BinaryValue;
import com.serotonin.mango.rt.dataImage.types.MangoValue;
import com.serotonin.mango.rt.dataImage.types.MultistateValue;
import com.serotonin.mango.rt.dataImage.types.NumericValue;
import javax.script.Invocable;

/**
 * @author Matthew Lohbihler
 */
public class ScriptExecutor {

    private static final String SCRIPT_PREFIX = "function __scriptExecutor__() {";
    private static final String SCRIPT_SUFFIX = "\r\n}";
    private static String SCRIPT_FUNCTION_PATH;
    private static String FUNCTIONS;

    public static void setScriptFunctionPath(String path) {
        SCRIPT_FUNCTION_PATH = path;
    }

    public Map<String, IDataPoint> convertContext(List<IntValuePair> context) throws DataPointStateException {
        RuntimeManager rtm = Common.ctx.getRuntimeManager();

        Map<String, IDataPoint> converted = new HashMap<>();
        for (IntValuePair contextEntry : context) {
            DataPointRT point = rtm.getDataPoint(contextEntry.getKey());
            if (point == null) {
                throw new DataPointStateException(contextEntry.getKey(), "event.meta.pointMissing");
            }
            converted.put(contextEntry.getValue(), point);
        }

        return converted;
    }

    public PointValueTime execute(String script, Map<String, IDataPoint> context, long runtime,
            DataType dataType, long timestamp) throws ScriptException, ResultTypeException {
        ensureFunctions();

        // Create the script engine.
        ScriptEngineManager manager;
        try {
            manager = new ScriptEngineManager();
        } catch (Exception e) {
            throw new ScriptException(e);
        }
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        // engine.getContext().setErrorWriter(new PrintWriter(System.err));
        // engine.getContext().setWriter(new PrintWriter(System.out));

        // Create the wrapper object context.
        WrapperContext wrapperContext = new WrapperContext(runtime);

        // Add constants to the context.
        engine.put("SECOND", TimePeriods.SECONDS.mangoDbId);
        engine.put("MINUTE", TimePeriods.MINUTES.mangoDbId);
        engine.put("HOUR", TimePeriods.HOURS.mangoDbId);
        engine.put("DAY", TimePeriods.DAYS.mangoDbId);
        engine.put("WEEK", TimePeriods.WEEKS.mangoDbId);
        engine.put("MONTH", TimePeriods.MONTHS.mangoDbId);
        engine.put("YEAR", TimePeriods.YEARS.mangoDbId);
        engine.put("CONTEXT", wrapperContext);

        // Put the context variables into the engine with engine scope.
        for (String varName : context.keySet()) {
            IDataPoint point = context.get(varName);
            DataType dt = point.getDataType();
            switch (dt) {
                case BINARY:
                    engine.put(varName, new BinaryPointWrapper(point, wrapperContext));
                    break;
                case MULTISTATE:
                    engine.put(varName, new MultistatePointWrapper(point, wrapperContext));
                    break;
                case NUMERIC:
                    engine.put(varName, new NumericPointWrapper(point, wrapperContext));
                    break;
                case ALPHANUMERIC:
                    engine.put(varName, new AlphanumericPointWrapper(point, wrapperContext));
                    break;
                default:
                    throw new ShouldNeverHappenException("Unknown data type : " + point.getDataType());
            }
        }

        // Create the script.
        script = SCRIPT_PREFIX + script + SCRIPT_SUFFIX + FUNCTIONS;

        // Execute.
        Object result = null;
        try {
            engine.eval(script);
            Invocable inv = (Invocable) engine;
            result = inv.invokeFunction("__scriptExecutor__");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (ScriptException e) {
            throw prettyScriptMessage(e);
        }

        // Check if a timestamp was set
        Object ts = engine.get("TIMESTAMP");
        if (ts != null) {
            // Check the type of the object.
            if (ts instanceof Number) // Convert to long
            {
                timestamp = ((Number) ts).longValue();
            }
            // else if (ts instanceof ScriptableObject && "Date".equals(((ScriptableObject)ts).getClassName())) {
            // // A native date
            // // It turns out to be a crazy hack to try and get the value from a native date, and the Rhino source
            // // code FTP server is not responding, so, going to have to leave this for now.
            // }
        }

        MangoValue value;
        if (result == null) {
            switch (dataType) {
                case BINARY:
                    value = new BinaryValue(false);
                    break;
                case MULTISTATE:
                    value = new MultistateValue(0);
                    break;
                case NUMERIC:
                    value = new NumericValue(0);
                    break;
                case ALPHANUMERIC:
                    value = new AlphanumericValue("");
                    break;
                default:
                    value = null;
            }
        } else if (result instanceof AbstractPointWrapper) {
            value = ((AbstractPointWrapper) result).getValueImpl();
        } // See if the type matches.
        else if (dataType == DataType.BINARY && result instanceof Boolean) {
            value = new BinaryValue((Boolean) result);
        } else if (dataType == DataType.MULTISTATE && result instanceof Number) {
            value = new MultistateValue(((Number) result).intValue());
        } else if (dataType == DataType.NUMERIC && result instanceof Number) {
            value = new NumericValue(((Number) result).doubleValue());
        } else if (dataType == DataType.ALPHANUMERIC && result instanceof String) {
            value = new AlphanumericValue((String) result);
        } else // If not, ditch it.
        {
            throw new ResultTypeException("event.script.convertError", result, dataType);
        }

        return new PointValueTime(value, timestamp);
    }

    public static ScriptException prettyScriptMessage(ScriptException e) {
        while (e.getCause() instanceof ScriptException) {
            e = (ScriptException) e.getCause();
        }

        // Try to make the error message look a bit nicer.
        List<String> exclusions = new ArrayList<>();
        exclusions.add("sun.org.mozilla.javascript.internal.EcmaError: ");
        exclusions.add("sun.org.mozilla.javascript.internal.EvaluatorException: ");
        String message = e.getMessage();
        for (String exclude : exclusions) {
            if (message.startsWith(exclude)) {
                message = message.substring(exclude.length());
            }
        }
        return new ScriptException(message, e.getFileName(), e.getLineNumber(), e.getColumnNumber());
    }

    private static void ensureFunctions() {
        if (FUNCTIONS == null) {
            if (SCRIPT_FUNCTION_PATH == null) {
                SCRIPT_FUNCTION_PATH = Common.ctx.getServletContext().getRealPath("/WEB-INF/scripts/scriptFunctions.js");
            }
            StringWriter sw = new StringWriter();
            FileReader fr = null;
            try {
                fr = new FileReader(SCRIPT_FUNCTION_PATH);
                StreamUtils.transfer(fr, sw);
            } catch (IOException e) {
                throw new ShouldNeverHappenException(e);
            } finally {
                try {
                    if (fr != null) {
                        fr.close();
                    }
                } catch (IOException e) {
                    // no op
                }
            }
            FUNCTIONS = sw.toString();
        }
    }
}
