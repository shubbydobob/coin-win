import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

plugins {
    java
    jacoco
    checkstyle
    alias(libs.plugins.springBoot)
    alias(libs.plugins.dependencyManagement)
    alias(libs.plugins.spotbugs)
}

group = "com.coinwin"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        // Boot 4.1 은 Java 17~26 을 지원하지만 이 프로젝트는 21 LTS 로 고정한다.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// ---------------------------------------------------------------------------
// archFixture 소스셋
//
// ArchUnit 규칙이 "실제로 위반을 잡는지" 증명하기 위한, 의도적으로 규칙을 어기는 코드다.
// 루트 패키지가 com.coinwin 이 아닌 archfixture 이므로 정상 규칙 검사(importPackages("com.coinwin"))에
// 절대 섞이지 않는다. 프로덕션이 아니므로 Checkstyle/SpotBugs/JaCoCo 대상에서 제외한다.
// 근거: .claude/docs/roadmap.md Phase 0 완료 조건
// ---------------------------------------------------------------------------
val archFixture: SourceSet = sourceSets.create("archFixture")

sourceSets {
    test {
        // ArchitectureRulesViolationTest 가 픽스처 클래스를 읽을 수 있게 한다.
        runtimeClasspath += archFixture.output
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(libs.springdoc.openapi.webmvc)

    // Phase 3 — 캔들 저장. Hibernate 가 아니라 JdbcClient 를 쓰는 근거는 docs/adr/011.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // 픽스처는 @Component 같은 최소 스텁만 필요하다.
    "archFixtureCompileOnly"("org.springframework:spring-context")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.archunit.junit6)

    // H2 금지(testing.md). 통합 테스트는 실제 PostgreSQL 을 띄운다.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(libs.testcontainers.postgresql)

    // Gradle 9 + JUnit 6 조합에서 명시하지 않으면 "Failed to load JUnit Platform" 으로 깨진다.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

// ---------------------------------------------------------------------------
// 테스트 태스크
//
// 통합 테스트는 Docker 가 필요하므로 기본 `test` 에서 태그로 걷어낸다. 근거는
// .claude/docs/testing.md — "태그로 분리해 기본 test 에서 제외, 별도 실행".
// 소스셋을 나누지 않고 태그만 쓰는 이유는 LoadCandlesPort 계약 스위트 하나를 세 어댑터가
// 공유하기 때문이다. 소스셋이 갈리면 그 공유가 깨진다.
// ---------------------------------------------------------------------------
val integrationTag = "integration"

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        // -PshowTestOutput 으로 테스트 표준출력을 켠다.
        // ArchUnit 위반 메시지 원문을 증거로 확인할 때 쓴다.
        showStandardStreams = providers.gradleProperty("showTestOutput").isPresent
    }
    // compose.yaml 과 Testcontainers 가 같은 이미지를 쓰게 한다. 출처는 libs.versions.toml.
    systemProperty("coinwin.postgres.image", libs.versions.postgresImage.get())
}

tasks.test {
    useJUnitPlatform { excludeTags(integrationTag) }
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Testcontainers 로 실제 PostgreSQL 을 띄우는 통합 테스트. Docker 가 필요하다."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags(integrationTag) }
    shouldRunAfter(tasks.test)
}

// ---------------------------------------------------------------------------
// Checkstyle — 수치 근거: .claude/docs/conventions.md 복잡도 한계표
// ---------------------------------------------------------------------------
checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configFile = file("config/checkstyle/checkstyle.xml")
    // checkstyle.xml 의 ${config_loc} 이 이 디렉터리로 해석된다 (suppressions.xml 참조용).
    configDirectory = file("config/checkstyle")
    isIgnoreFailures = false
    maxWarnings = 0
}

// 테스트 소스는 MethodLength/FileLength 만 완화한다. 근거는 conventions.md 에 기록.
tasks.named<Checkstyle>("checkstyleTest") {
    configFile = file("config/checkstyle/checkstyle-test.xml")
}

// 의도적으로 나쁜 코드이므로 정적 분석을 적용하지 않는다.
tasks.named<Checkstyle>("checkstyleArchFixture") { enabled = false }

// ---------------------------------------------------------------------------
// SpotBugs
// ---------------------------------------------------------------------------
spotbugs {
    toolVersion = libs.versions.spotbugs.get()
    effort = Effort.MAX
    reportLevel = Confidence.DEFAULT
    excludeFilter = file("config/spotbugs/exclude.xml")
    ignoreFailures = false
}

tasks.named<SpotBugsTask>("spotbugsArchFixture") { enabled = false }
tasks.named<SpotBugsTask>("spotbugsTest") { enabled = false }

tasks.named<SpotBugsTask>("spotbugsMain") {
    reports.create("html") { required = true }
    reports.create("xml") { required = false }
}

// ---------------------------------------------------------------------------
// JaCoCo — domain 패키지 커버리지 게이트
// 근거: .claude/docs/testing.md "domain 패키지 90% 미만이면 빌드 실패"
// ---------------------------------------------------------------------------
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "CLASS"
            // 모든 모듈의 domain 패키지. 게이트가 켜져 있는지는 검증 V2 에서 확인한다.
            includes = listOf("com.coinwin.*.domain.*")
            excludes = listOf("com.coinwin.CoinWinApplication", "archfixture.*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
    // 리포트도 함께 갱신한다. 그러지 않으면 build/reports 의 커버리지 수치가
    // 마지막 수동 실행 시점에 멈춰 있어, 오래된 숫자를 현재 상태로 착각하게 된다.
    dependsOn(tasks.jacocoTestReport)
    // 픽스처가 컴파일되지 않으면 위반 테스트가 조용히 무의미해진다.
    dependsOn(archFixture.classesTaskName)
}
