/*
 * Copyright 2017 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.gaellalire.vestige.jvm_enhancer.boot;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ProxySelector;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.btr.proxy.search.desktop.gnome.ProxySchemasGSettingsAccess;
import com.btr.proxy.search.desktop.win.Win32ProxyUtils;
import com.btr.proxy.util.Logger.LogBackEnd;

import fr.gaellalire.vestige.core.StackedHandler;
import fr.gaellalire.vestige.core.Vestige;
import fr.gaellalire.vestige.core.VestigeClassLoaderConfiguration;
import fr.gaellalire.vestige.core.VestigeCoreContext;
import fr.gaellalire.vestige.core.executor.VestigeExecutor;
import fr.gaellalire.vestige.core.executor.VestigeWorker;
import fr.gaellalire.vestige.core.executor.callable.InvokeMethod;
import fr.gaellalire.vestige.core.function.Function;
import fr.gaellalire.vestige.core.parser.NoStateStringParser;
import fr.gaellalire.vestige.core.parser.StringParser;
import fr.gaellalire.vestige.core.resource.JarFileResourceLocator;
import fr.gaellalire.vestige.core.resource.VestigeResourceLocator;
import fr.gaellalire.vestige.jpms.JPMSAccessorLoader;
import fr.gaellalire.vestige.jpms.JPMSModuleAccessor;
import fr.gaellalire.vestige.jpms.JPMSModuleLayerAccessor;
import fr.gaellalire.vestige.jvm_enhancer.runtime.JULBackend;
import fr.gaellalire.vestige.jvm_enhancer.runtime.SystemProxySelector;
import fr.gaellalire.vestige.jvm_enhancer.runtime.WeakArrayList;
import fr.gaellalire.vestige.jvm_enhancer.runtime.WeakHashtable;
import fr.gaellalire.vestige.jvm_enhancer.runtime.WeakIdentityHashMap;
import fr.gaellalire.vestige.jvm_enhancer.runtime.WeakJceSecurityHashMap;
import fr.gaellalire.vestige.jvm_enhancer.runtime.WeakLevelMap;
import fr.gaellalire.vestige.jvm_enhancer.runtime.WeakProviderConcurrentHashMap;
import fr.gaellalire.vestige.jvm_enhancer.runtime.windows.WindowsShutdownHook;

/**
 * @author Gael Lalire
 */
public class JVMEnhancer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JVMEnhancer.class);

    private static Object getField(final Field field) throws Exception {
        Callable<Object> callable = new Callable<Object>() {

            @Override
            public Object call() throws Exception {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                    try {
                        return field.get(null);
                    } finally {
                        field.setAccessible(false);
                    }
                } else {
                    return field.get(null);
                }
            }
        };
        if (Modifier.isFinal(field.getModifiers())) {
            return FinalUnsetter.unsetFinalField(field, callable);
        } else {
            return callable.call();
        }
    }

    private static void setField(final Field field, final Object value) throws Exception {
        Callable<Object> callable = new Callable<Object>() {

            @Override
            public Object call() throws Exception {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                    try {
                        field.set(null, value);
                    } finally {
                        field.setAccessible(false);
                    }
                } else {
                    field.set(null, value);
                }
                return null;
            }
        };
        if (Modifier.isFinal(field.getModifiers())) {
            FinalUnsetter.unsetFinalField(field, callable);
        } else {
            callable.call();
        }
    }

    private File directory;

    private Class<?> mainClass;

    private String[] dargs;

    private VestigeCoreContext vestigeCoreContext;

    private List<? extends ClassLoader> privilegedClassloaders;

    private Function<Thread, Void, RuntimeException> addShutdownHook;

    private Function<Thread, Void, RuntimeException> removeShutdownHook;

    public void runEnhancedMain() throws Exception {
        try {
            Method method = mainClass.getMethod("vestigeEnhancedCoreMain", VestigeCoreContext.class, Function.class, Function.class, List.class, String[].class);
            method.invoke(null, new Object[] {vestigeCoreContext, addShutdownHook, removeShutdownHook, privilegedClassloaders, dargs});
        } catch (NoSuchMethodException e) {
            runMain();
        }
    }

    public void runMain() throws Exception {
        Vestige.runMain(null, mainClass, vestigeCoreContext, dargs);
    }

    public JVMEnhancer(final File directory, final VestigeCoreContext vestigeCoreContext, final Class<?> mainClass, final String[] dargs) {
        this.directory = directory;
        this.vestigeCoreContext = vestigeCoreContext;
        this.mainClass = mainClass;
        this.dargs = dargs;
    }

    public Class<?> getMainClass() {
        return mainClass;
    }

    public String[] getDargs() {
        return dargs;
    }

    public VestigeCoreContext getVestigeCoreContext() {
        return vestigeCoreContext;
    }

    public VestigeExecutor getVestigeExecutor() {
        return vestigeCoreContext.getVestigeExecutor();
    }

    public Function<Thread, Void, RuntimeException> getAddShutdownHook() {
        return addShutdownHook;
    }

    public Function<Thread, Void, RuntimeException> getRemoveShutdownHook() {
        return removeShutdownHook;
    }

    public List<? extends ClassLoader> getPrivilegedClassloaders() {
        return privilegedClassloaders;
    }

    public File getDirectory() {
        return directory;
    }

    public ClassLoader createClassLoader(final VestigeWorker vestigeWorker, final String runtimePaths) throws Exception {
        List<File> urlList = new ArrayList<File>();
        Vestige.addClasspath(directory, urlList, runtimePaths);
        VestigeResourceLocator[] urls = new VestigeResourceLocator[urlList.size()];
        int i = 0;
        for (File file : urlList) {
            urls[i] = new JarFileResourceLocator(file);
            i++;
        }
        StringParser stringParser = new NoStateStringParser(0);
        // create classloader with executor to remove this protection domain from access control
        VestigeClassLoaderConfiguration[][] vestigeClassLoaderConfigurationsArray = new VestigeClassLoaderConfiguration[1][];
        vestigeClassLoaderConfigurationsArray[0] = new VestigeClassLoaderConfiguration[] {VestigeClassLoaderConfiguration.THIS_PARENT_SEARCHED};
        return vestigeWorker.createVestigeClassLoader(ClassLoader.getSystemClassLoader(), vestigeClassLoaderConfigurationsArray, stringParser, stringParser, null, urls);
    }

    @SuppressWarnings("unchecked")
    public void boot(final String propertyPath) throws Exception {
        JPMSModuleAccessor javaBaseModule;
        if (JPMSAccessorLoader.INSTANCE != null) {
            JPMSModuleLayerAccessor bootLayer = JPMSAccessorLoader.INSTANCE.bootLayer();
            bootLayer.findModule("java.desktop").addExports("sun.awt", InvokeMethod.class);
            javaBaseModule = bootLayer.findModule("java.base");
            javaBaseModule.addExports("sun.security.jca", InvokeMethod.class);
            javaBaseModule.addOpens("java.lang.reflect", JVMEnhancer.class);
            javaBaseModule.addOpens("javax.crypto", JVMEnhancer.class);
            javaBaseModule.addOpens("java.net", JVMEnhancer.class);
        } else {
            javaBaseModule = null;
        }

        Properties properties = new Properties();
        if (propertyPath.length() != 0) {
            FileInputStream fileInputStream = new FileInputStream(propertyPath);
            try {
                properties.load(fileInputStream);
            } finally {
                fileInputStream.close();
            }
        }

        VestigeWorker vestigeWorker = vestigeCoreContext.getVestigeExecutor().createWorker("bootstrap-sun-worker", true, 0);

        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

        LOGGER.debug("Calling sun.awt.AppContext.getAppContext");
        try {
            // synchronization issue with -XstartOnFirstThread (java.awt.Toolkit needs to be loaded by first thread)
            Class.forName("java.awt.Toolkit", true, systemClassLoader);
            // keep the context classloader in static field (mainAppContext.contextClassLoader)
            Class<?> appContextClass = vestigeWorker.classForName(systemClassLoader, "sun.awt.AppContext");
            vestigeWorker.invoke(systemClassLoader, appContextClass.getMethod("getAppContext"), null);
        } catch (Exception e) {
            LOGGER.trace("sun.awt.AppContext.getAppContext call failed", e);
        }
        LOGGER.debug("Loading javax.security.auth.Policy");
        try {
            // keep the context classloader in static field
            vestigeWorker.classForName(systemClassLoader, "javax.security.auth.Policy");
        } catch (Exception e) {
            LOGGER.trace("javax.security.auth.Policy load failed", e);
        }
        LOGGER.debug("Loading com.sun.org.apache.xerces.internal.dom.DOMNormalizer");
        try {
            // keep an exception in static field
            vestigeWorker.classForName(systemClassLoader, "com.sun.org.apache.xerces.internal.dom.DOMNormalizer");
        } catch (Exception e) {
            LOGGER.trace("com.sun.org.apache.xerces.internal.dom.DOMNormalizer load failed", e);
        }
        LOGGER.debug("Loading com.sun.org.apache.xml.internal.serialize.DOMSerializerImpl");
        try {
            // keep an exception in static field
            vestigeWorker.classForName(systemClassLoader, "com.sun.org.apache.xml.internal.serialize.DOMSerializerImpl");
        } catch (Exception e) {
            LOGGER.trace("com.sun.org.apache.xml.internal.serialize.DOMSerializerImpl load failed", e);
        }
        LOGGER.debug("Loading com.sun.org.apache.xerces.internal.parsers.AbstractDOMParser");
        try {
            // keep an exception in static field
            vestigeWorker.classForName(systemClassLoader, "com.sun.org.apache.xerces.internal.parsers.AbstractDOMParser");
        } catch (Exception e) {
            LOGGER.trace("com.sun.org.apache.xerces.internal.parsers.AbstractDOMParser load failed", e);
        }
        LOGGER.debug("Loading javax.management.remote.JMXServiceURL");
        try {
            // keep an exception in static field
            vestigeWorker.classForName(systemClassLoader, "javax.management.remote.JMXServiceURL");
        } catch (Exception e) {
            LOGGER.trace("javax.management.remote.JMXServiceURL load failed", e);
        }
        LOGGER.debug("Loading sun.java2d.Disposer");
        try {
            // create thread
            vestigeWorker.classForName(systemClassLoader, "sun.java2d.Disposer");
        } catch (Exception e) {
            LOGGER.trace("sun.java2d.Disposer load failed", e);
        }
        LOGGER.debug("Calling javax.security.auth.login.Configuration.getConfiguration");
        try {
            // keep the context classloader in static field
            Class<?> configurationClass = vestigeWorker.classForName(systemClassLoader, "javax.security.auth.login.Configuration");
            vestigeWorker.invoke(systemClassLoader, configurationClass.getMethod("getConfiguration"), null);
        } catch (Throwable e) {
            LOGGER.trace("javax.security.auth.login.Configuration.getConfiguration call failed", e);
        }
        LOGGER.debug("Calling sun.security.jca.ProviderList.getService");
        try {
            // sun.security.pkcs11.SunPKCS11 create thread (with the context
            // classloader of parent thread) and sun.security.jca.Providers keep
            // it in static field
            Class<?> providersClass = vestigeWorker.classForName(systemClassLoader, "sun.security.jca.Providers");
            Object providerList = vestigeWorker.invoke(systemClassLoader, providersClass.getMethod("getProviderList"), null);
            Class<?> providerListClass = vestigeWorker.classForName(systemClassLoader, "sun.security.jca.ProviderList");
            vestigeWorker.invoke(systemClassLoader, providerListClass.getMethod("getService", String.class, String.class), providerList, "MessageDigest", "SHA");
        } catch (Exception e) {
            LOGGER.trace("sun.security.jca.ProviderList.getService call failed", e);
        }
        LOGGER.debug("Calling sun.misc.GC.requestLatency");
        try {
            // sun.misc.GC create thread (with the context classloader of parent
            // thread)
            Class<?> gcClass = vestigeWorker.classForName(systemClassLoader, "sun.misc.GC");
            vestigeWorker.invoke(systemClassLoader, gcClass.getMethod("requestLatency", long.class), null, Long.valueOf(Long.MAX_VALUE - 1));
        } catch (Exception e) {
            LOGGER.trace("sun.misc.GC.requestLatency call failed", e);
        }
        LOGGER.debug("Loading com.sun.jndi.ldap.LdapPoolManager");
        try {
            // com.sun.jndi.ldap.LdapPoolManager may create thread (with the
            // context
            // classloader of parent thread)
            vestigeWorker.classForName(systemClassLoader, "com.sun.jndi.ldap.LdapPoolManager");
        } catch (Exception e) {
            LOGGER.trace("com.sun.jndi.ldap.LdapPoolManager load failed", e);
        }

        String runtimePaths = properties.getProperty("runtime.jar");

        if (runtimePaths == null) {
            privilegedClassloaders = Collections.emptyList();
        } else {
            ClassLoader vestigeClassLoader = createClassLoader(vestigeWorker, runtimePaths);

            LOGGER.debug("Replacing javax.crypto.JceSecurity.verificationResults");
            try {
                Field verificationResultsField = Class.forName("javax.crypto.JceSecurity").getDeclaredField("verificationResults");
                if (getField(verificationResultsField) instanceof ConcurrentHashMap) {
                    Class<?> weakProviderConcurrentHashMapClass = vestigeClassLoader.loadClass(WeakProviderConcurrentHashMap.class.getName());
                    if (javaBaseModule != null) {
                        // Properties
                        javaBaseModule.addOpens("java.util", weakProviderConcurrentHashMapClass);
                        // get provider field
                        javaBaseModule.addOpens("javax.crypto", weakProviderConcurrentHashMapClass);
                    }
                    setField(verificationResultsField, weakProviderConcurrentHashMapClass.getConstructor().newInstance());
                } else {
                    Class<?> weakIdentityHashMapClass = vestigeClassLoader.loadClass(WeakIdentityHashMap.class.getName());
                    setField(verificationResultsField, weakIdentityHashMapClass.getConstructor().newInstance());
                }
            } catch (Exception e) {
                LOGGER.trace("javax.crypto.JceSecurity.verificationResults replacement failed", e);
            } catch (NoClassDefFoundError e) {
                LOGGER.trace("javax.crypto.JceSecurity.verificationResults replacement failed", e);
            }

            LOGGER.debug("Replacing javax.crypto.JceSecurity.codeBaseCacheRef");
            try {
                Class<?> weakJceSecurityHashMapClass = vestigeClassLoader.loadClass(WeakJceSecurityHashMap.class.getName());
                setField(Class.forName("javax.crypto.JceSecurity").getDeclaredField("codeBaseCacheRef"), weakJceSecurityHashMapClass.getConstructor().newInstance());
            } catch (Exception e) {
                LOGGER.trace("javax.crypto.JceSecurity.codeBaseCacheRef replacement failed", e);
            } catch (NoClassDefFoundError e) {
                LOGGER.trace("javax.crypto.JceSecurity.codeBaseCacheRef replacement failed", e);
            }

            LOGGER.debug("Replacing javax.crypto.JceSecurity.verifyingProviders");
            try {
                Class<?> weakIdentityHashMapClass = vestigeClassLoader.loadClass(WeakIdentityHashMap.class.getName());
                setField(Class.forName("javax.crypto.JceSecurity").getDeclaredField("verifyingProviders"), weakIdentityHashMapClass.getConstructor().newInstance());
            } catch (Exception e) {
                LOGGER.trace("javax.crypto.JceSecurity.verifyingProviders replacement failed", e);
            } catch (NoClassDefFoundError e) {
                LOGGER.trace("javax.crypto.JceSecurity.verifyingProviders replacement failed", e);
            }

            LOGGER.debug("Replacing java.net.URL.handlers");
            try {
                Class<?> weakHashtableClass = vestigeClassLoader.loadClass(WeakHashtable.class.getName());
                setField(Class.forName("java.net.URL").getDeclaredField("handlers"), weakHashtableClass.getConstructor().newInstance());
            } catch (Exception e) {
                LOGGER.trace("java.net.URL.handlers replacement failed", e);
            } catch (NoClassDefFoundError e) {
                LOGGER.trace("java.net.URL.handlers replacement failed", e);
            }

            LOGGER.debug("Replacing java.lang.Thread.subclassAudits");
            try {
                // WeakSoftCache is hard to compile : the .class is in resources
                Class<?> weakSoftCacheClass = vestigeClassLoader.loadClass("fr.gaellalire.vestige.jvm_enhancer.runtime.WeakSoftCache");
                setField(Thread.class.getDeclaredField("subclassAudits"), weakSoftCacheClass.getConstructor().newInstance());
            } catch (Exception e) {
                LOGGER.trace("java.lang.Thread.subclassAudits replacement failed", e);
            } catch (NoClassDefFoundError e) {
                LOGGER.trace("java.lang.Thread.subclassAudits replacement failed", e);
            }

            // keep levels in static field
            LOGGER.debug("Replacing java.util.logging.Level.known");
            try {
                Field declaredField = Level.class.getDeclaredField("known");
                List<Level> known = (List<Level>) getField(declaredField);

                Class<?> weakArrayListClass = vestigeClassLoader.loadClass(WeakArrayList.class.getName());
                List<Level> weakArrayList = (List<Level>) weakArrayListClass.getConstructor(Level.class).newInstance(Level.OFF);
                weakArrayList.addAll(known);
                setField(declaredField, weakArrayList);
            } catch (NoSuchFieldException nsfe) {
                LOGGER.debug("Replacing java.util.logging.Level$KnownLevel");
                try {
                    Class<?> forName = Class.forName("java.util.logging.Level$KnownLevel");
                    Field nameToLevelsField = forName.getDeclaredField("nameToLevels");
                    Field intToLevelsField = forName.getDeclaredField("intToLevels");
                    Field levelObjectField = forName.getDeclaredField("levelObject");
                    Constructor<?> constructor = forName.getDeclaredConstructor(Level.class);
                    levelObjectField.setAccessible(true);
                    constructor.setAccessible(true);

                    Map<String, List<Object>> initialNameToLevels = (Map<String, List<Object>>) getField(nameToLevelsField);

                    Class<?> weakLevelMapClass = vestigeClassLoader.loadClass(WeakLevelMap.class.getName());
                    Constructor<?> weakLevelMapConstructor = weakLevelMapClass.getConstructor(Field.class, Constructor.class);

                    Map<String, List<Object>> nameToLevels = (Map<String, List<Object>>) weakLevelMapConstructor.newInstance(levelObjectField, constructor);
                    for (Entry<String, List<Object>> entry : initialNameToLevels.entrySet()) {
                        List<Object> list = nameToLevels.get(entry.getKey());
                        list.addAll(entry.getValue());
                    }
                    Map<Integer, List<Object>> initialIntToLevels = (Map<Integer, List<Object>>) getField(intToLevelsField);
                    Map<Integer, List<Object>> intToLevels = (Map<Integer, List<Object>>) weakLevelMapConstructor.newInstance(levelObjectField, constructor);
                    for (Entry<Integer, List<Object>> entry : initialIntToLevels.entrySet()) {
                        List<Object> list = intToLevels.get(entry.getKey());
                        list.addAll(entry.getValue());
                    }
                    setField(nameToLevelsField, nameToLevels);
                    setField(intToLevelsField, intToLevels);
                } catch (Exception e) {
                    LOGGER.trace("java.util.logging.Level$KnownLevel replacement failed", e);
                }
            } catch (Exception e) {
                LOGGER.trace("java.util.logging.Level.known replacement failed", e);
            }

            String osName = System.getProperty("os.name").toLowerCase();
            boolean windows = osName.contains("windows");
            boolean mac = osName.contains("mac");
            String arch = "x86";
            if (!System.getProperty("os.arch").equals("w32")) {
                arch = System.getProperty("os.arch");
                if (arch.equals("x86_64")) {
                    arch = "amd64";
                }
            }

            // install proxy selector
            try {
                synchronized (ProxySelector.class) {
                    if (windows) {
                        String proxyUtilPath = properties.getProperty("proxy_vole.proxy_util." + arch);
                        if (proxyUtilPath != null) {
                            File proxyUtilFile = new File(directory, proxyUtilPath);
                            Class<?> win32ProxyUtilsClass = vestigeClassLoader.loadClass(Win32ProxyUtils.class.getName());
                            Method method = win32ProxyUtilsClass.getMethod("init", String.class);
                            try {
                                vestigeWorker.invoke(vestigeClassLoader, method, null, proxyUtilFile.getAbsolutePath());
                            } catch (Exception e) {
                                LOGGER.warn("Unable to load windows proxy accessor", e);
                            }
                        }
                    } else if (!mac) {
                        String gsettingsPath = properties.getProperty("proxy_vole.gsettings." + arch);
                        if (gsettingsPath != null) {
                            File gsettingsFile = new File(directory, gsettingsPath);
                            Class<?> proxySchemasGSettingsAccessClass = vestigeClassLoader.loadClass(ProxySchemasGSettingsAccess.class.getName());
                            Method method = proxySchemasGSettingsAccessClass.getMethod("init", String.class);
                            try {
                                vestigeWorker.invoke(vestigeClassLoader, method, null, gsettingsFile.getAbsolutePath());
                            } catch (Exception e) {
                                LOGGER.warn("Unable to load linux proxy accessor", e);
                            }
                        }
                    }

                    // redirect log to JUL
                    Class<?> julBackendClass = vestigeClassLoader.loadClass(JULBackend.class.getName());
                    Class<?> loggerClass = vestigeClassLoader.loadClass(com.btr.proxy.util.Logger.class.getName());
                    loggerClass.getMethod("setBackend", vestigeClassLoader.loadClass(LogBackEnd.class.getName())).invoke(null, julBackendClass.getConstructor().newInstance());

                    ProxySelector proxySelector = ProxySelector.getDefault();
                    Class<?> systemProxySelectorClass = vestigeClassLoader.loadClass(SystemProxySelector.class.getName());
                    Object systemProxySelector = systemProxySelectorClass.getConstructor().newInstance();
                    ((StackedHandler<ProxySelector>) systemProxySelector).setNextHandler(proxySelector);
                    ProxySelector.setDefault((ProxySelector) systemProxySelector);
                }
            } catch (Throwable e) {
                LOGGER.warn("Unable to install proxy vole", e);
            }

            // install exit handler
            if (windows) {
                try {
                    String shutdownHookPath = properties.getProperty("shutdownHook." + arch);
                    if (shutdownHookPath != null) {
                        File shutdownHookFile = new File(directory, shutdownHookPath);

                        Class<?> windowsShutdownHookClass = vestigeClassLoader.loadClass(WindowsShutdownHook.class.getName());
                        Method method = windowsShutdownHookClass.getMethod("init", String.class);
                        vestigeWorker.invoke(vestigeClassLoader, method, null, shutdownHookFile.getAbsolutePath());
                        addShutdownHook = (Function<Thread, Void, RuntimeException>) windowsShutdownHookClass.getField("ADD_SHUTDOWN_HOOK_FUNCTION").get(null);
                        removeShutdownHook = (Function<Thread, Void, RuntimeException>) windowsShutdownHookClass.getField("REMOVE_SHUTDOWN_HOOK_FUNCTION").get(null);
                    }
                } catch (Throwable e) {
                    LOGGER.warn("Unable to install windows shutdown hook", e);
                }
            }
            privilegedClassloaders = Collections.singletonList(vestigeClassLoader);
        }

        vestigeWorker.interrupt();
        vestigeWorker.join();

        runEnhancedMain();
    }

    public static void vestigeCoreMain(final VestigeCoreContext vestigeCoreContext, final String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expecting at least 3 arg : directory, properties, mainClass");
        }
        String[] dargs = new String[args.length - 3];
        System.arraycopy(args, 3, dargs, 0, dargs.length);
        new JVMEnhancer(new File(args[0]), vestigeCoreContext, Thread.currentThread().getContextClassLoader().loadClass(args[2]), dargs).boot(args[1]);
    }

    public static void main(final String[] args) throws Exception {
        VestigeCoreContext vestigeCoreContext = VestigeCoreContext.buildDefaultInstance();
        URL.setURLStreamHandlerFactory(vestigeCoreContext.getStreamHandlerFactory());
        vestigeCoreMain(vestigeCoreContext, args);
    }

}
