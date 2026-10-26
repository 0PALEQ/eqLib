package com.cookiecraftmods.eqlib.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Resolves the renamed Minecraft method once, without linking to either version's name. */
public final class SeatCompatibility {
    private static final MethodHandle INVULNERABLE = resolveInvulnerability();

    private SeatCompatibility() {}

    private static MethodHandle resolveInvulnerability() {
        MethodType signature = MethodType.methodType(void.class, boolean.class);
        NoSuchMethodException missing = null;
        for (String name : new String[] {"setPermanentlyInvulnerable", "setInvulnerable"}) {
            try {
                return MethodHandles.publicLookup().findVirtual(ArmorStand.class, name, signature);
            } catch (NoSuchMethodException absent) {
                missing = absent;
            } catch (IllegalAccessException inaccessible) {
                throw new ExceptionInInitializerError(inaccessible);
            }
        }
        throw new ExceptionInInitializerError(missing);
    }

    public static void makeInvulnerable(ArmorStand seat) {
        try {
            INVULNERABLE.invokeExact(seat, true);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not configure the seat mount", failure);
        }
    }
}
