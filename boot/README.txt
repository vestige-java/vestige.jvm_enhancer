Add main class if you recreate a module-info.class :

jar --main-class fr.gaellalire.vestige.jvm_enhancer.boot.JPMSJVMEnhancer --update --file target/vestige.jvm_enhancer.boot*.jar; \
pushd src/main/resources/ && jar xf ../../../target/vestige.jvm_enhancer.boot*.jar module-info.class; popd
