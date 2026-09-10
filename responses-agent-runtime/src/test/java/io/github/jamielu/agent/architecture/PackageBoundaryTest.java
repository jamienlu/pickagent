package io.github.jamielu.agent.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "io.github.jamielu.agent",
        importOptions = ImportOption.DoNotIncludeTests.class)
class PackageBoundaryTest {
    // 场景：扫描核心生产包依赖；行为：执行架构规则；预期：核心不依赖 OpenAI、在线、离线或示例代码。
    @ArchTest
    static final ArchRule CORE_IS_PROVIDER_NEUTRAL = noClasses()
            .that().resideInAnyPackage(
                    "..api..", "..runtime..", "..tool..", "..reliability..", "..internal..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..openai..", "..online..", "..offline..", "..example..");

    // 场景：扫描 OpenAI 适配包依赖；行为：执行架构规则；预期：适配器不反向依赖组合入口或离线固定数据。
    @ArchTest
    static final ArchRule OPENAI_ADAPTER_HAS_NO_COMPOSITION_DEPENDENCY = noClasses()
            .that().resideInAPackage("..openai..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..online..", "..offline..", "..example..");
}
