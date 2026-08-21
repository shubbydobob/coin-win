import com.github.gradle.node.npm.task.NpmTask
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
    alias(libs.plugins.node)
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
    // flyway-core 만으로는 마이그레이션이 돌지 않는다. Boot 4 는 자동 구성을 기술별 모듈로
    // 쪼갰고 배선은 스타터에만 들어 있다 — 라이브러리가 있어도 스프링이 부르지 않는다.
    // Phase 7 의 통합 테스트가 이것을 잡았다. 그전까지는 통합 테스트가 Flyway 를 손으로
    // 돌려서(Flyway.configure()...migrate()) 앱 기동 경로가 한 번도 검증되지 않았다.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Phase 5 — 매매 기록. 캔들과 달리 JPA 를 쓴다. 근거는 docs/adr/016.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation(libs.querydsl.jpa)
    // 클래스파이어 jpa: @Entity 를 읽어 Q 클래스를 만드는 프로세서다. 빼면 Q 클래스가 생기지 않는다.
    annotationProcessor(variantOf(libs.querydsl.apt) { classifier("jpa") })
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // Phase 7 — Spring AI. 근거는 docs/adr/005 (범위)와 docs/spec/phase7-spring-ai.md (배선).
    implementation(platform(libs.spring.ai.bom))
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")

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

// 트레이딩뷰 대조는 진짜 거래소를 때리고 값이 매번 다르다. 회귀 테스트가 될 수 없으므로
// 기본 test 에서 걷어낸다. 근거는 docs/adr/015 — 외부 기준값은 사람이 한 번 대조한다.
val crossCheckTag = "crosscheck"

// AI 호출은 결정론적이지 않고, 네트워크와 키와 비용을 요구한다. 기본 test 는 스텁으로만
// 돌고 실제 모델 대조는 사람이 돌린다. crossCheck 와 같은 이유, 같은 형태다.
// 근거: docs/spec/phase7-spring-ai.md § 9.2
val liveAiTag = "liveAi"

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
    useJUnitPlatform { excludeTags(integrationTag, crossCheckTag, liveAiTag) }
}

tasks.register<Test>("crossCheck") {
    group = "verification"
    description = "실제 BTCUSDT 캔들의 지표값을 출력한다. 트레이딩뷰와 눈으로 대조하는 용도."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags(crossCheckTag) }
    // 출력을 보는 것이 목적이므로 -PshowTestOutput 없이도 항상 찍는다.
    testLogging { showStandardStreams = true }
    // 거래소 값이 매번 다르다. UP-TO-DATE 로 건너뛰면 대조할 표가 나오지 않는다.
    outputs.upToDateWhen { false }
}

tasks.register<Test>("liveAi") {
    group = "verification"
    description = "실제 OpenAI 를 부른다. OPENAI_API_KEY 가 필요하고 비용이 든다."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags(liveAiTag) }
    testLogging { showStandardStreams = true }
    // 모델 응답이 매번 다르다. UP-TO-DATE 로 건너뛰면 대조할 것이 나오지 않는다.
    outputs.upToDateWhen { false }
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

// ---------------------------------------------------------------------------
// 프론트엔드 (Phase 8)
//
// 게이트는 하나다 — `.\gradlew.bat check` 가 자바와 프론트를 함께 검사한다. 게이트가 둘이면
// 하나는 반드시 안 돌게 된다. 근거: docs/spec/phase8-frontend.md § 2 · § 9.1
//
// 플러그인이 지정 버전 Node 를 내려받는다. 로컬 설치본을 부르지 않으므로 Node 버전이
// 사람마다 달라도 같은 결과가 나오고, Windows 의 npm.cmd 도 플러그인이 다룬다.
// ---------------------------------------------------------------------------
val frontendDir = layout.projectDirectory.dir("frontend")

node {
    version = libs.versions.node.get()
    download = true
    nodeProjectDir = frontendDir
}

val frontendCheck = tasks.register<NpmTask>("frontendCheck") {
    group = "verification"
    description = "프론트엔드 게이트 — tsc --noEmit · eslint · vitest run"
    dependsOn(tasks.npmInstall)
    npmCommand = listOf("run", "check")

    // 입력이 그대로면 다시 돌지 않는다. 출력이 없는 검사이므로 표식 파일을 하나 남긴다 —
    // 그것이 없으면 Gradle 이 매번 처음부터 돌리거나, 반대로 영원히 UP-TO-DATE 로 본다.
    inputs.dir(frontendDir.dir("src"))
    inputs.dir(frontendDir.dir("test"))
    inputs.files(
        frontendDir.file("package.json"),
        frontendDir.file("package-lock.json"),
        frontendDir.file("tsconfig.json"),
        frontendDir.file("vite.config.ts"),
        frontendDir.file("eslint.config.js"),
    )
    val marker = layout.buildDirectory.file("frontend/check.marker")
    outputs.file(marker)
    doLast {
        val file = marker.get().asFile
        file.parentFile.mkdirs()
        file.writeText("ok")
    }
}

tasks.check {
    dependsOn(frontendCheck)
    dependsOn(tasks.jacocoTestCoverageVerification)
    // 리포트도 함께 갱신한다. 그러지 않으면 build/reports 의 커버리지 수치가
    // 마지막 수동 실행 시점에 멈춰 있어, 오래된 숫자를 현재 상태로 착각하게 된다.
    dependsOn(tasks.jacocoTestReport)
    // 픽스처가 컴파일되지 않으면 위반 테스트가 조용히 무의미해진다.
    dependsOn(archFixture.classesTaskName)
}
