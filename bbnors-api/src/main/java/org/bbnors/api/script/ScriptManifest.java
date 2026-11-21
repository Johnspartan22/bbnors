package org.bbnors.api.script;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadata for a script, read by {@link ScriptRegistry}. Optional: unannotated scripts are listed
 * under their class's simple name.
 *
 * <pre>{@code
 * @ScriptManifest(id = "woodcutter", name = "Woodcutter", version = "1.0",
 *                 description = "Chops trees and banks the logs.")
 * public final class Woodcutter extends TaskScript { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ScriptManifest {

    /** Unique, stable identifier used to select the script ([a-z0-9-]+). */
    String id();

    /** Display name. */
    String name() default "";

    String description() default "";

    String version() default "1.0";

    String author() default "";

    /** Free-form grouping, e.g. "skilling", "combat", "test". */
    String category() default "";
}
