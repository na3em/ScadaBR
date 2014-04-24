/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.org.scadabr.timer.cron;

import br.org.scadabr.timer.CronTask;
import java.text.ParseException;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 *
 * @author aploese
 */
public abstract class EventCronTask extends CronTask {

    protected EventCronTask(CronExpression ce) {
        super(ce);
    }

    protected EventCronTask(String pattern, TimeZone tz) throws ParseException {
        super(pattern, tz);
    }
}
