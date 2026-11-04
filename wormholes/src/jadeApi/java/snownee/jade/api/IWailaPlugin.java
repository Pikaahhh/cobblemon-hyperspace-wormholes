package snownee.jade.api;

/** Compile-time stand-in for Jade's plugin interface (the parts compat.JadePlugin overrides); never packaged. */
public interface IWailaPlugin {
    default void register(IWailaCommonRegistration registration) {
    }

    default void registerClient(IWailaClientRegistration registration) {
    }
}
