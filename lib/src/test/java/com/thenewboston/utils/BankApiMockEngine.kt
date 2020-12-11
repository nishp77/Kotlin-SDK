package com.thenewboston.utils

import com.thenewboston.data.dto.bankapi.common.request.UpdateTrustRequest
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Accepted
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class BankApiMockEngine {

    fun getSuccess() = getBankMockEngine()

    fun getErrors() = getBankMockEngine(sendOnlyErrorResponses = true)

    fun patchSuccess() = patchBankEngine()

    fun patchEmptySuccess() = patchBankEngine(isInvalidResponse = true)

    fun patchErrors() = patchBankEngine(true)

    private val json = listOf(ContentType.Application.Json.toString())
    private val responseHeaders = headersOf("Content-Type" to json)

    private fun getBankMockEngine(sendOnlyErrorResponses: Boolean = false) =
        HttpClient(MockEngine) {
            val errorContent = BankAPIJsonMapper.mapInternalServerErrorToJson()
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        BankAPIJsonMapper.ACCOUNTS_ENDPOINT -> {
                            val content = BankAPIJsonMapper.mapAccountsToJson()
                            sendResponse(content, errorContent, sendOnlyErrorResponses)
                        }
                        BankAPIJsonMapper.BANKS_ENDPOINT -> {
                            val content = BankAPIJsonMapper.mapBanksToJson()
                            sendResponse(content, errorContent, sendOnlyErrorResponses)
                        }
                        BankAPIJsonMapper.BANK_TRANSACTIONS_ENDPOINT -> {
                            val content = BankAPIJsonMapper.mapBankTransactionsToJson()
                            sendResponse(content, errorContent, sendOnlyErrorResponses)
                        }
                        BankAPIJsonMapper.BLOCKS_ENDPOINT -> {
                            val content = BankAPIJsonMapper.mapBlocksToJson()
                            sendResponse(content, errorContent, sendOnlyErrorResponses)
                        }
                        BankAPIJsonMapper.VALIDATORS_ENDPOINT -> {
                            val content = BankAPIJsonMapper.mapValidatorsToJson()
                            sendResponse(content, errorContent, sendOnlyErrorResponses)
                        }
                        BankAPIJsonMapper.SINGLE_VALIDATOR_ENDPOINT -> {
                            val content = BankAPIJsonMapper.mapValidatorToJson()
                            sendResponse(content, errorContent, sendOnlyErrorResponses)
                        }
                        BankAPIJsonMapper.CONFIG_ENDPOINT -> {
                            val content = BankAPIJsonMapper.mapBankDetailToJson()
                            sendResponse(content, errorContent, sendOnlyErrorResponses)
                        }
                        else -> {
                            error("Unhandled ${request.url.encodedPath}")
                        }
                    }
                }
            }

            installJsonFeature()
        }

    private fun MockRequestHandleScope.sendResponse(
        content: String,
        errorContent: String,
        isError: Boolean
    ) = when {
        isError -> respond(errorContent, InternalServerError, responseHeaders)
        else -> respond(content, HttpStatusCode.OK, responseHeaders)
    }

    private fun patchBankEngine(
        enableErrorResponse: Boolean = false,
        isInvalidResponse: Boolean = false
    ) = HttpClient(MockEngine) {
        val errorContent = BankAPIJsonMapper.mapInternalServerErrorToJson()

        engine {
            addHandler { request ->
                when {
                    request.url.encodedPath == BankAPIJsonMapper.BANKS_TRUST_ENDPOINT -> {
                        val content = BankAPIJsonMapper.mapBankTrustResponseToJson()
                        val invalidContent = BankAPIJsonMapper.mapInvalidBankTrustResponseToJson()
                        when {
                            enableErrorResponse -> respond(
                                errorContent,
                                InternalServerError,
                                responseHeaders
                            )
                            isInvalidResponse -> respond(
                                invalidContent,
                                Accepted,
                                responseHeaders
                            )
                            else -> respond(content, Accepted, responseHeaders)
                        }
                    }
                    request.url.encodedPath.startsWith(BankAPIJsonMapper.ACCOUNTS_ENDPOINT) -> {
                        val requestBodyString = (request.body as TextContent).text
                        val requestedTrust =
                            Json.decodeFromString<UpdateTrustRequest>(requestBodyString).message.trust
                        val responseBody =
                            BankAPIJsonMapper.mapAccountToJson(trust = requestedTrust)
                        when {
                            enableErrorResponse -> respond(
                                errorContent,
                                InternalServerError,
                                responseHeaders
                            )
                            isInvalidResponse -> respond(
                                BankAPIJsonMapper.mapEmptyAccountToJson(),
                                Accepted,
                                responseHeaders
                            )
                            else -> respond(responseBody, Accepted, responseHeaders)
                        }
                    }
                    else -> {
                        error("Unhandled ${request.url.encodedPath}")
                    }
                }
            }
        }

        installJsonFeature()

        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    private fun HttpClientConfig<MockEngineConfig>.installJsonFeature() {
        install(JsonFeature) {
            serializer = KotlinxSerializer(json())
        }
    }

    private fun json(): Json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }
}
