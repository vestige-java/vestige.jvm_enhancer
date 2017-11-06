module fr.gaellalire.vestige.jvm_enhancer.runtime {

    requires java.logging;

    requires fr.gaellalire.vestige.core;

    requires fr.gaellalire.vestige.proxy_vole;

    exports fr.gaellalire.vestige.jvm_enhancer.runtime;

    exports fr.gaellalire.vestige.jvm_enhancer.runtime.windows;
}
