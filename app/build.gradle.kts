import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Credenciais de assinatura, lidas de um ficheiro fora do controlo de versoes.
 *
 * Sem o ficheiro — noutra maquina, ou em CI sem os segredos — a release compila mas sai **nao
 * assinada**, em vez de a build falhar. E o comportamento certo: quem so quer compilar nao precisa
 * da chave de publicacao, e quem precisa de publicar sabe que a tem de ter.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.mysky.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mysky.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Base URL da fonte de voos por omissão. Trocar de fonte não deve exigir
        // mudanças na app: ver FlightDataSource (data/source).
        buildConfigField("String", "OPENSKY_BASE_URL", "\"https://opensky-network.org/api/\"")
    }

    signingConfigs {
        create("release") {
            keystoreProperties.getProperty("storeFile")?.let { path ->
                storeFile = file(path)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            // So assina se as credenciais existirem; senao sai nao assinado (ver acima).
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            // KT-73255: anotacoes em parametros de construtor (@ApplicationContext, qualifiers do
            // Hilt) passam a aplicar-se tambem ao campo, que e o que o Dagger espera.
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // A tabela de rotas tem de ficar **por comprimir** dentro do APK. A app lê-a por
        // deslocamento direto, com pesquisa binária, sem a carregar para memória (AD-013); um
        // asset comprimido só é legível de forma sequencial, o que obrigaria a descomprimir 7,6 MB
        // para o heap a cada arranque — e nada daria erro. O `noCompress` é por extensão.
        noCompress += "bin"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

// Exporta os esquemas do Room para app/schemas: obrigatorio para escrever testes de migracao.
ksp {
    arg("room.schemaLocation", file("schemas").absolutePath)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager + DataStore
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    // Widget (Glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.okhttp.logging.interceptor)

    // Localização e permissões
    implementation(libs.play.services.location)
    implementation(libs.accompanist.permissions)

    // Testes unitários (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Testes instrumentados
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
