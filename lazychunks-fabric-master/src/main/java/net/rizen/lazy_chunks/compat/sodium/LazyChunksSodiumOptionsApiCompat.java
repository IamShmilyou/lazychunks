package net.rizen.lazy_chunks.compat.sodium;

import net.rizen.lazy_chunks.LazyChunksMod;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public final class LazyChunksSodiumOptionsApiCompat {
    private static final String FABRIC_EVENT = "net.fabricmc.fabric.api.event.Event";
    private static final String OPTION_GUI_CONSTRUCTION = "toni.sodiumoptionsapi.api.OptionGUIConstruction";
    private static boolean registered;

    private LazyChunksSodiumOptionsApiCompat() {
    }

    public static boolean register() {
        if (registered) {
            return true;
        }

        try {
            ClassLoader classLoader = LazyChunksSodiumOptionsApiCompat.class.getClassLoader();
            Class<?> eventClass = Class.forName(FABRIC_EVENT, false, classLoader);
            Class<?> optionGuiConstructionClass = Class.forName(OPTION_GUI_CONSTRUCTION, false, classLoader);
            Object event = optionGuiConstructionClass.getField("EVENT").get(null);
            Object listener = Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[] { optionGuiConstructionClass },
                    new LazyChunksOptionGuiConstructionHandler()
            );

            Method registerMethod = eventClass.getMethod("register", Object.class);
            registerMethod.invoke(event, listener);

            registered = true;
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            LazyChunksMod.LOGGER.warn("Sodium Options API is present, but LazyChunks could not register through it", error);
            return false;
        }
    }

    private static final class LazyChunksOptionGuiConstructionHandler implements InvocationHandler {
        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("onGroupConstruction".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof List<?>) {
                ((List<Object>) args[0]).add(LazyChunksLegacySodiumPage.create());
            }

            return null;
        }
    }
}
