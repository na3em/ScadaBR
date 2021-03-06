/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.org.scadabr.vo.datasource.meta;

import br.org.scadabr.vo.datasource.*;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

/**
 *
 * @author aploese
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = CronPatternValidator.class)
public @interface CronPattern {

    public String message() default "{validate.cron}";

    public Class<?>[] groups() default {};

    public Class<? extends Payload>[] payload() default {};

}
