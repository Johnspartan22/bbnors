package org.bbnors.api;

import org.bbnors.api.script.Script;
import org.bbnors.api.script.ScriptContext;
import org.bbnors.api.script.ScriptManifest;
import org.bbnors.api.script.ScriptRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class ScriptRegistryTest {

    @ScriptManifest(id = "demo", name = "Demo", version = "2.1", description = "test script")
    public static final class Demo implements Script {
        @Override public int loop(ScriptContext ctx) { return -1; }
    }

    public static final class Plain implements Script {
        @Override public int loop(ScriptContext ctx) { return -1; }
    }

    @ScriptManifest(id = "demo")
    public static final class Clash implements Script {
        @Override public int loop(ScriptContext ctx) { return -1; }
    }

    @Test
    public void registersWithManifestMetadataAndCreatesFreshInstances() {
        ScriptRegistry r = new ScriptRegistry();
        ScriptRegistry.Entry e = r.register(Demo.class, Demo::new);
        assertEquals("demo", e.id());
        assertEquals("2.1", e.version());
        assertNotSame(r.create("demo"), r.create("demo"));
        r.register(Plain.class, Plain::new);
        assertEquals(java.util.List.of("demo", "plain"), r.ids());
    }

    @Test(expected = IllegalStateException.class)
    public void duplicateIdsAreRejected() {
        ScriptRegistry r = new ScriptRegistry();
        r.register(Demo.class, Demo::new);
        r.register(Clash.class, Clash::new);
    }

    @Test
    public void unknownIdListsAvailableScripts() {
        ScriptRegistry r = new ScriptRegistry();
        r.register(Demo.class, Demo::new);
        try {
            r.create("nope");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("demo"));
            return;
        }
        throw new AssertionError("expected failure");
    }
}
