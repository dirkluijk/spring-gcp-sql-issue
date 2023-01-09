package com.example.demo

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.HttpRequestInitializer
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.spring.core.GcpScope.SQLADMIN
import com.google.cloud.sql.CredentialFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.bind.PlaceholdersResolver.NONE
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.Ordered.LOWEST_PRECEDENCE
import org.springframework.core.annotation.Order
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*

@Order(LOWEST_PRECEDENCE)
class CustomCloudSqlEnvironmentPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment?, application: SpringApplication?) {
        if (environment!!.propertySources.contains("bootstrap")) {
            return
        }

        val binder = Binder(ConfigurationPropertySources.get(environment), NONE, null, null, null)

        val customPropertiesPrefix = CustomSpringProperties::class.java
            .getAnnotation(ConfigurationProperties::class.java)
            .value

        val customProperties = binder
            .bind(customPropertiesPrefix, CustomSpringProperties::class.java)
            .orElse(CustomSpringProperties())

        val encodedKey = customProperties.credentials.encodedKey

        System.setProperty(
            CustomCredentialFactory.CREDENTIAL_ENCODED_KEY_PROPERTY_NAME,
            encodedKey
        )

        System.setProperty(
            CredentialFactory.CREDENTIAL_FACTORY_PROPERTY,
            CustomCredentialFactory::class.java.name
        )
    }
}

@Component
class CustomCredentialFactory : CredentialFactory {

    override fun create(): HttpRequestInitializer {
        val encodedKey = System.getProperty(CREDENTIAL_ENCODED_KEY_PROPERTY_NAME)
        val credentialsInputStream: InputStream = ByteArrayInputStream(Base64.getDecoder().decode(encodedKey))
        val googleCredential = GoogleCredential.fromStream(credentialsInputStream).createScoped(setOf(SQLADMIN.url))

        val googleCredentials = GoogleCredentialsWithFakeRefresh(
            AccessToken(
                googleCredential.accessToken,
                Date(googleCredential.expirationTimeMilliseconds)
            )
        )

        return HttpCredentialsAdapter(googleCredentials)
    }

    companion object {
        const val CREDENTIAL_ENCODED_KEY_PROPERTY_NAME = "DEMO_GCP_CREDENTIALS_ENCODED_KEY"
    }
}

class GoogleCredentialsWithFakeRefresh(accessToken: AccessToken) : GoogleCredentials(accessToken) {
    override fun refreshAccessToken(): AccessToken {
        // just return same access token...
        return super.getAccessToken()
    }
}

@ConfigurationProperties("demo.gcp")
@ConstructorBinding
data class CustomSpringProperties(
    @NestedConfigurationProperty val credentials: com.google.cloud.spring.core.Credentials = com.google.cloud.spring.core.Credentials()
)