package au.com.dius.pact.provider.reporters

import au.com.dius.pact.core.model.BasePact
import au.com.dius.pact.core.model.FileSource
import au.com.dius.pact.core.model.Interaction
import au.com.dius.pact.core.model.Pact
import au.com.dius.pact.core.model.PactSource
import au.com.dius.pact.core.model.PactSpecVersion
import au.com.dius.pact.core.model.UrlPactSource
import au.com.dius.pact.core.support.Json
import au.com.dius.pact.core.support.hasProperty
import au.com.dius.pact.core.support.property
import au.com.dius.pact.provider.IConsumerInfo
import au.com.dius.pact.provider.IProviderInfo
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.jsonArray
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.set
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.apache.commons.lang3.exception.ExceptionUtils
import java.io.File
import java.time.ZonedDateTime

/**
 * Pact verifier reporter that generates the results of the verification in JSON format
 */
class JsonReporter(
  var name: String = "json",
  override var reportDir: File,
  var jsonData: JsonObject = JsonObject(),
  override var ext: String = ".json",
  private var providerName: String? = null
) : VerifierReporter {

  constructor(name: String, reportDir: File) : this(name, reportDir, JsonObject(), ".json", null)

  override lateinit var reportFile: File

  init {
    reportFile = File(reportDir, "$name$ext")
  }

  override fun initialise(provider: IProviderInfo) {
    providerName = provider.name
    jsonData = jsonObject(
      "metaData" to jsonObject(
        "date" to ZonedDateTime.now().toString(),
        "pactJvmVersion" to BasePact.lookupVersion(),
        "reportFormat" to REPORT_FORMAT
      ),
      "provider" to jsonObject("name" to providerName),
      "execution" to jsonArray()
    )
    reportDir.mkdirs()
    reportFile = File(reportDir, providerName + ext)
  }

  override fun finaliseReport() {
    if (reportFile.exists() && reportFile.length() > 0) {
      val existingContents = JsonParser().parse(reportFile.readText())
      if (providerName == existingContents["provider"].obj["name"].string) {
        existingContents["metaData"] = jsonData["metaData"]
        existingContents["execution"].array.addAll(jsonData["execution"].array)
        reportFile.writeText(existingContents.toString())
      } else {
        reportFile.writeText(jsonData.toString())
      }
    } else {
      reportFile.writeText(jsonData.toString())
    }
  }

  override fun reportVerificationForConsumer(consumer: IConsumerInfo, provider: IProviderInfo, tag: String?) {
    jsonData["execution"].array.add(jsonObject(
      "consumer" to jsonObject("name" to consumer.name),
      "interactions" to jsonArray()
    ))
  }

  override fun verifyConsumerFromUrl(pactUrl: UrlPactSource, consumer: IConsumerInfo) {
    jsonData["execution"].array.last()["consumer"].obj["source"] = jsonObject("url" to pactUrl.url)
  }

  override fun verifyConsumerFromFile(pactFile: PactSource, consumer: IConsumerInfo) {
    jsonData["execution"].array.last()["consumer"].obj["source"] = jsonObject(
      "file" to if (pactFile is FileSource<*>) pactFile.file else pactFile.description()
    )
  }

  override fun pactLoadFailureForConsumer(consumer: IConsumerInfo, message: String) {
    jsonData["execution"].array.last()["result"] = jsonObject(
      "state" to "Pact Load Failure",
      "message" to message
    )
  }

  override fun warnProviderHasNoConsumers(provider: IProviderInfo) { }

  override fun warnPactFileHasNoInteractions(pact: Pact<Interaction>) { }

  override fun interactionDescription(interaction: Interaction) {
    jsonData["execution"].array.last()["interactions"].array.add(jsonObject(
      "interaction" to Json.toJson(interaction.toMap(PactSpecVersion.V3)),
      "verification" to jsonObject("result" to "OK")
    ))
  }

  override fun stateForInteraction(
    state: String,
    provider: IProviderInfo,
    consumer: IConsumerInfo,
    isSetup: Boolean
  ) { }

  override fun warnStateChangeIgnored(state: String, provider: IProviderInfo, consumer: IConsumerInfo) { }

  override fun stateChangeRequestFailedWithException(
    state: String,
    provider: IProviderInfo,
    consumer: IConsumerInfo,
    isSetup: Boolean,
    e: Exception,
    printStackTrace: Boolean
  ) {
  }

  override fun stateChangeRequestFailed(state: String, provider: IProviderInfo, isSetup: Boolean, httpStatus: String) {
  }

  override fun warnStateChangeIgnoredDueToInvalidUrl(
    state: String,
    provider: IProviderInfo,
    isSetup: Boolean,
    stateChangeHandler: Any
  ) { }

  override fun requestFailed(
    provider: IProviderInfo,
    interaction: Interaction,
    interactionMessage: String,
    e: Exception,
    printStackTrace: Boolean
  ) {
    jsonData["execution"].array.last()["interactions"].array.last()["verification"] = jsonObject(
      "result" to FAILED,
      "message" to interactionMessage,
      "exception" to jsonObject(
        "message" to e.message,
        "stackTrace" to ExceptionUtils.getStackFrames(e)
      )
    )
  }

  override fun returnsAResponseWhich() { }

  override fun statusComparisonOk(status: Int) { }

  override fun statusComparisonFailed(status: Int, comparison: Any) {
    val verification = jsonData["execution"].array.last()["interactions"].array.last()["verification"]
    verification["result"] = FAILED
    val statusJson = jsonArray(
      if (comparison.hasProperty("message")) {
        comparison.property("message")?.get(comparison).toString().split('\n')
      } else {
        comparison.toString().split('\n')
      }
    )
    verification["status"] = statusJson
  }

  override fun includesHeaders() { }

  override fun headerComparisonOk(key: String, value: List<String>) { }

  override fun headerComparisonFailed(key: String, value: List<String>, comparison: Any) {
    val verification = jsonData["execution"].array.last()["interactions"].array.last()["verification"].obj
    verification["result"] = FAILED
    if (!verification.has("header")) {
      verification["header"] = jsonObject()
    }
    verification["header"].obj[key] = Json.toJson(comparison)
  }

  override fun bodyComparisonOk() { }

  override fun bodyComparisonFailed(comparison: Any) {
    val verification = jsonData["execution"].array.last()["interactions"].array.last()["verification"].obj
    verification["result"] = FAILED
    verification["body"] = Json.toJson(comparison)
  }

  override fun errorHasNoAnnotatedMethodsFoundForInteraction(interaction: Interaction) {
    jsonData["execution"].array.last()["interactions"].array.last()["verification"] = jsonObject(
      "result" to FAILED,
      "cause" to jsonObject("message" to "No Annotated Methods Found For Interaction")
    )
  }

  override fun verificationFailed(interaction: Interaction, e: Exception, printStackTrace: Boolean) {
    jsonData["execution"].array.last()["interactions"].array.last()["verification"] = jsonObject(
      "result" to FAILED,
      "exception" to jsonObject(
        "message" to e.message,
        "stackTrace" to ExceptionUtils.getStackFrames(e)
    )
    )
  }

  override fun generatesAMessageWhich() { }

  override fun displayFailures(failures: Map<String, Any>) { }

  override fun metadataComparisonFailed(key: String, value: Any?, comparison: Any) {
    val verification = jsonData["execution"].array.last()["interactions"].array.last()["verification"].obj
    verification["result"] = FAILED
    if (!verification.has("metadata")) {
      verification["metadata"] = jsonObject()
    }
    verification["metadata"].obj[key] = comparison
  }

  override fun includesMetadata() { }

  override fun metadataComparisonOk(key: String, value: Any?) { }

  override fun metadataComparisonOk() { }

  companion object {
    const val REPORT_FORMAT = "0.0.0"
    const val FAILED = "failed"
  }
}
