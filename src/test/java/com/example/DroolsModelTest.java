package com.example;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.drools.compiler.kie.builder.impl.KieBuilderImpl;
import org.drools.model.Model;
import org.drools.modelcompiler.ExecutableModelProject;
import org.drools.modelcompiler.builder.KieBaseBuilder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.io.KieResources;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;


@TestMethodOrder(MethodOrderer.MethodName.class)
class DroolsModelTest {

    @Test
    void propertySpecificAlways() {
        System.out.println("# propertySpecificAlways");
        System.setProperty("drools.propertySpecific", "ALWAYS");

        executeTest(this::buildKieBase);
    }

    @Test
    void propertySpecificAlways_extractClasses() {
        System.out.println("# propertySpecificAlways_extractClasses");
        System.setProperty("drools.propertySpecific", "ALWAYS");

        executeTest(this::buildKieBaseFromExtractedModel);
    }

    @Test
    void propertySpecificDisabled() {
        System.out.println("# propertySpecificDisabled");
        System.setProperty("drools.propertySpecific", "DISABLED");

        executeTest(this::buildKieBase);
    }


    @Test
    void propertySpecificDisabled_extractClasses() {
        System.out.println("# propertySpecificDisabled_extractClasses");
        System.setProperty("drools.propertySpecific", "DISABLED");

        executeTest(this::buildKieBaseFromExtractedModel);
    }

    private void executeTest(Function<List<String>, KieBase> getKieBase) {
        KieBase kieBase = getKieBase.apply(List.of(getRule()));

        KieSession kieSession = kieBase.newKieSession();

        kieSession.addEventListener(new DefaultAgendaEventListener() {
            @Override
            public void beforeMatchFired(final BeforeMatchFiredEvent event) {
                System.out.println("==> " + event.getMatch().getRule().getName());
            }
        });

        kieSession.insert(new Gate(false, "mainGate"));

        kieSession.fireAllRules();

        System.out.println(
            "Results: " + kieSession.getObjects(String.class::isInstance).stream().toList());
    }

    private static String getRule() {
        return """
            package rules
            dialect "java"
                            
            import com.example.IGate
                        
            rule "Valid gate"
            when
                IGate(valid)
            then
                insertLogical("Valid");
            end
                        
                     
            rule "Indifferent Gate"
            when
                IGate()
            then
                insertLogical("Indifferent");
            end
                        
            rule "Invalid Gate"
            when
                $gate : IGate(!valid)
            then
                modify ($gate) {
                    setValid(true)
                }
            end
                        
            """;
    }

    KieBase buildKieBase(List<String> fileContents) {
        createKieBuilder(fileContents);
        KieContainer kieContainer = KieServices.get().newKieContainer(KieServices.get().getRepository()
                                                                                 .getDefaultReleaseId());
        return kieContainer.newKieBase(KieServices.get().newKieBaseConfiguration(null));
    }

    KieBase buildKieBaseFromExtractedModel(List<String> fileContents) {
        KieBuilder kieBuilder = createKieBuilder(fileContents);
        MemoryFileSystem trgMfs = ((KieBuilderImpl) kieBuilder).getTrgMfs();
        DefaultByteArrayClassLoader cl = new DefaultByteArrayClassLoader(this.getClass().getClassLoader());
        List<? extends Class<?>> classes = trgMfs.getMap().entrySet()
                                                 .stream()
                                                 .filter(entry -> entry.getKey().endsWith(".class"))
                                                 .map(entry -> {
                                                         String fileName = entry.getKey();

                                                         String className = fileName.replace("/", ".")
                                                                                    .replace(".class", "");
                                                         return cl.defineClass(className, entry.getValue());
                                                     }
                                                 ).toList();

        return createKieBaseFromModelClass(classes);
    }

    private static KieBuilder createKieBuilder(final List<String> fileContents) {
        KieFileSystem kieFileSystem = KieServices.get().newKieFileSystem();

        var i = 1;
        for (String fileContent : fileContents) {
            final KieResources kieResources = KieServices.get().getResources();
            final Resource resource = kieResources.newInputStreamResource(
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8.toString()
            );
            resource.setTargetPath("rules/drl%s.drl".formatted(i));
            resource.setResourceType(ResourceType.DRL);
            kieFileSystem.write(resource);
            i++;
        }

        KieBuilder kieBuilder = KieServices.get().newKieBuilder(kieFileSystem);
        kieBuilder.buildAll(ExecutableModelProject.class);
        return kieBuilder;
    }


    private KieBase createKieBaseFromModelClass(final List<? extends Class<?>> classes) {
        return classes.stream()
                      .filter(Model.class::isAssignableFrom)
                      .findFirst()
                      .map(c -> {
                          try {
                              return c.getDeclaredConstructor();
                          } catch (NoSuchMethodException e) {
                              throw new RuntimeException(e);
                          }
                      })
                      .map(c -> {
                          try {
                              return c.newInstance();
                          } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                              throw new RuntimeException(e);
                          }
                      })
                      .map(Model.class::cast)
                      .map(KieBaseBuilder::createKieBaseFromModel)
                      .orElseThrow(() -> new RuntimeException("No Model class found"));
    }

    public static class DefaultByteArrayClassLoader extends ClassLoader {

        public DefaultByteArrayClassLoader(final ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineClass(
            final String name,
            final byte[] bytes
        ) {
            return defineClass(
                name,
                bytes,
                0,
                bytes.length,
                null
            );
        }
    }
}

