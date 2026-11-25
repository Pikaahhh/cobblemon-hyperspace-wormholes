package snownee.jade.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compile-time stand-in for the annotation Jade finds plugins by (compat.JadePlugin); never packaged. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WailaPlugin {
    String value() default "";
}
