/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.org.scadabr.sync;

import br.org.scadabr.ImplementMeException;
import com.serotonin.mango.web.servlet.AsyncImageChartServlet;
import java.util.concurrent.ExecutorService;

/**
 *
 * @author aploese
 */
public class Synchronizer<T extends Object> {

    public void addTask(T t) {
        throw new ImplementMeException();
    }

    public void executeAndWait(ExecutorService executorService) {
        throw new ImplementMeException();
    }

    public int getSize() {
        throw new ImplementMeException();
    }

    public Iterable<T> getTasks() {
        throw new ImplementMeException();
    }

}
