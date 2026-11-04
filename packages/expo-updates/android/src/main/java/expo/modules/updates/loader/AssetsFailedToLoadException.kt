package expo.modules.updates.loader

import expo.modules.updates.logging.AssetFailureGroup
import expo.modules.updates.logging.LoadFailures
import java.io.IOException

data class AssetLoadFailure(val assetKey: String?, val error: Exception)

/**
 * Thrown when one or more assets of an update fail to load, and carries all of the failures.
 * Remote loads log a warning for each failed asset download. Its cause is the first failure.
 */
class AssetsFailedToLoadException(val failures: List<AssetLoadFailure>) : IOException(
  message(failures),
  failures.firstOrNull()?.error
) {
  companion object {
    private const val MAX_LISTED_ASSET_KEYS = 5

    /**
     * Groups the asset failures in [error], or in an error that it wraps, by whether the device
     * could reach the server and by HTTP status, in the order each group first fails. Returns null
     * when no asset failed to load.
     */
    fun failureGroups(error: Throwable): List<AssetFailureGroup>? {
      val exception = generateSequence(error) { it.cause }
        .filterIsInstance<AssetsFailedToLoadException>()
        .firstOrNull()
        ?: return null
      return exception.failures
        .groupBy { failure -> Pair(LoadFailures.isServerUnreachable(failure.error), httpStatus(failure.error)) }
        .map { (key, groupFailures) ->
          AssetFailureGroup(groupFailures.map { it.assetKey }, key.second, groupFailures.first().error)
        }
    }

    private fun httpStatus(error: Throwable): Int? =
      generateSequence(error) { it.cause }
        .filterIsInstance<FileDownloader.AssetHttpResponseException>()
        .firstOrNull()
        ?.statusCode

    private fun message(failures: List<AssetLoadFailure>): String {
      val assetKeys = failures.map { it.assetKey ?: "(no key)" }
      val listedKeys = assetKeys.take(MAX_LISTED_ASSET_KEYS).joinToString()
      val moreKeys = if (assetKeys.size > MAX_LISTED_ASSET_KEYS) {
        ", and ${assetKeys.size - MAX_LISTED_ASSET_KEYS} more"
      } else {
        ""
      }
      val assets = if (failures.size == 1) {
        "asset"
      } else {
        "assets"
      }
      return "Failed to load ${failures.size} $assets ($listedKeys$moreKeys)"
    }
  }
}
