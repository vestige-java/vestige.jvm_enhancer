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
import java.lang.ModuleLayer.Controller;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import fr.gaellalire.vestige.core.JPMSVestige;
import fr.gaellalire.vestige.core.ModuleEncapsulationEnforcer;
import fr.gaellalire.vestige.core.VestigeClassLoaderConfiguration;
import fr.gaellalire.vestige.core.VestigeCoreContext;
import fr.gaellalire.vestige.core.executor.VestigeExecutor;
import fr.gaellalire.vestige.core.function.Function;
import fr.gaellalire.vestige.core.parser.ListIndexStringParser;
import fr.gaellalire.vestige.core.parser.NoStateStringParser;
import fr.gaellalire.vestige.core.parser.ResourceEncapsulationEnforcer;
import fr.gaellalire.vestige.core.parser.StringParser;
import fr.gaellalire.vestige.core.resource.JarFileResourceLocator;
import fr.gaellalire.vestige.core.resource.VestigeResourceLocator;
import fr.gaellalire.vestige.core.url.DelegateURLStreamHandlerFactory;

/**
 * @author Gael Lalire
 */
public class JPMSJVMEnhancer extends JVMEnhancer {

    private Controller controller;

    private String mainModule;

    public JPMSJVMEnhancer(final File directory, final VestigeCoreContext vestigeCoreContext, final String mainClass, final String[] dargs, final Controller controller,
            final String mainModule) {
        super(directory, vestigeCoreContext, mainClass, dargs);
        this.controller = controller;
        this.mainModule = mainModule;
    }

    public ClassLoader createClassLoader(final String runtimePaths) throws Exception {
        ModuleLayer boot = ModuleLayer.boot();

        List<Path> pathList = new ArrayList<Path>();
        JPMSVestige.addModulepath(getDirectory(), pathList, runtimePaths);
        Path[] paths = new Path[pathList.size()];
        pathList.toArray(paths);

        VestigeResourceLocator[] urls = new VestigeResourceLocator[paths.length];
        for (int i = 0; i < paths.length; i++) {
            urls[i] = new JarFileResourceLocator(paths[i].toFile());
        }

        Configuration configuration = Configuration.resolve(ModuleFinder.of(), Collections.singletonList(boot.configuration()), ModuleFinder.of(paths),
                Set.of("fr.gaellalire.vestige.jvm_enhancer.runtime"));

        StringParser stringParser = new NoStateStringParser(0);
        Map<String, String> moduleNameByPackageName = new HashMap<>();
        Set<String> encapsulatedPackageNames = new HashSet<>();
        Map<File, String> moduleNamesByFile = new HashMap<>();
        List<String> moduleNames = new ArrayList<>(urls.length);

        JPMSVestige.createEnforcerConfiguration(moduleNamesByFile, moduleNameByPackageName, encapsulatedPackageNames, configuration);
        for (Path path : paths) {
            moduleNames.add(moduleNamesByFile.get(path.toFile().getAbsoluteFile()));
        }

        StringParser classStringParser = new NoStateStringParser(0);
        StringParser resourceStringParser = new ResourceEncapsulationEnforcer(classStringParser, encapsulatedPackageNames, -1);
        ModuleEncapsulationEnforcer moduleEncapsulationEnforcer = new ModuleEncapsulationEnforcer(moduleNameByPackageName, new ListIndexStringParser(moduleNames, -2), null);

        VestigeClassLoaderConfiguration[][] vestigeClassLoaderConfigurationsArray = new VestigeClassLoaderConfiguration[][] {
                new VestigeClassLoaderConfiguration[] {VestigeClassLoaderConfiguration.THIS_PARENT_SEARCHED}};

        // create classloader with executor to remove this protection domain from access control
        return getVestigeExecutor().createVestigeClassLoader(ClassLoader.getSystemClassLoader(), vestigeClassLoaderConfigurationsArray, stringParser, resourceStringParser,
                moduleEncapsulationEnforcer, urls);
    }

    public void runEnhancedMain() throws Exception {
        Optional<Module> findModule = controller.layer().findModule(mainModule);
        if (!findModule.isPresent()) {
            throw new IllegalArgumentException("Module " + mainModule + " cannot be found");
        }
        Module module = findModule.get();
        String mainClass = getMainClass();
        if (mainClass == null) {
            Optional<String> mainClassOptional = module.getDescriptor().mainClass();
            if (!mainClassOptional.isPresent()) {
                throw new IllegalArgumentException("Module " + mainModule + " has no main class");
            }
            mainClass = mainClassOptional.get();
            setMainClass(mainClass);
        }
        controller.addReads(JPMSJVMEnhancer.class.getModule(), module);
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        Class<?> loadClass = contextClassLoader.loadClass(mainClass);
        try {
            Method method = loadClass.getMethod("vestigeEnhancedCoreMain", VestigeCoreContext.class, Function.class, Function.class, List.class, Controller.class, String[].class);
            method.invoke(null, new Object[] {getVestigeCoreContext(), getAddShutdownHook(), getRemoveShutdownHook(), getPrivilegedClassloaders(), controller, getDargs()});
        } catch (NoSuchMethodException e) {
            super.runEnhancedMain();
        }
    }

    public static void vestigeCoreMain(final Controller controller, final VestigeCoreContext vestigeCoreContext, final String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expecting at least 3 args : directory, properties, mainModule[/mainClass]");
        }
        String mainModule = args[2];
        String mainClass = null;
        int indexOf = mainModule.indexOf('/');
        if (indexOf != -1) {
            mainClass = mainModule.substring(indexOf + 1);
            mainModule = mainModule.substring(0, indexOf);
        }

        String[] dargs = new String[args.length - 3];
        System.arraycopy(args, 3, dargs, 0, dargs.length);
        new JPMSJVMEnhancer(new File(args[0]), vestigeCoreContext, mainClass, dargs, controller, mainModule).boot(args[1]);
    }

    public static void vestigeCoreMain(final VestigeCoreContext vestigeCoreContext, final String[] args) throws Exception {
        vestigeCoreMain(null, vestigeCoreContext, args);
    }

    public static void main(final String[] args) throws Exception {
        DelegateURLStreamHandlerFactory streamHandlerFactory = new DelegateURLStreamHandlerFactory();
        URL.setURLStreamHandlerFactory(streamHandlerFactory);
        vestigeCoreMain(new VestigeCoreContext(streamHandlerFactory, new VestigeExecutor()), args);
    }

}
