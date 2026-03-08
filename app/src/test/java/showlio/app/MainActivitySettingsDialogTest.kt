package showlio.app

import android.widget.NumberPicker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import showlio.app.databinding.DialogSettingsBinding
import showlio.app.ui.viewer.ViewerUiState
import showlio.app.ui.viewer.ViewerViewModel

@RunWith(RobolectricTestRunner::class)
class MainActivitySettingsDialogTest {

    @Test
    fun `configureIntervalPicker applies bounds and clamps interval`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val picker = NumberPicker(activity)

        activity.invokeConfigureIntervalPicker(picker = picker, intervalSeconds = Int.MAX_VALUE)

        assertEquals(ViewerViewModel.MIN_INTERVAL_SECONDS, picker.minValue)
        assertEquals(ViewerViewModel.MAX_INTERVAL_SECONDS, picker.maxValue)
        assertEquals(NumberPicker.FOCUS_BLOCK_DESCENDANTS, picker.descendantFocusability)
        assertEquals(ViewerViewModel.MAX_INTERVAL_SECONDS, picker.value)

        activity.invokeConfigureIntervalPicker(picker = picker, intervalSeconds = Int.MIN_VALUE)

        assertEquals(ViewerViewModel.MIN_INTERVAL_SECONDS, picker.value)
    }

    @Test
    fun `checkedIdForMode maps random and sequential to expected radio ids`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val binding = DialogSettingsBinding.inflate(activity.layoutInflater)

        val randomId = activity.invokeCheckedIdForMode(binding, ViewerUiState.DisplayMode.RANDOM)
        val sequentialId = activity.invokeCheckedIdForMode(binding, ViewerUiState.DisplayMode.SEQUENTIAL)

        assertEquals(binding.displayModeRandom.id, randomId)
        assertEquals(binding.displayModeSequential.id, sequentialId)
    }

    @Test
    fun `modeFromSelection returns random when random radio is checked`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val binding = DialogSettingsBinding.inflate(activity.layoutInflater)
        binding.displayModeGroup.check(binding.displayModeRandom.id)

        val mode = activity.invokeModeFromSelection(binding)

        assertEquals(ViewerUiState.DisplayMode.RANDOM, mode)
    }

    @Test
    fun `modeFromSelection defaults to sequential when random radio is not checked`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val binding = DialogSettingsBinding.inflate(activity.layoutInflater)
        binding.displayModeGroup.check(binding.displayModeSequential.id)

        val mode = activity.invokeModeFromSelection(binding)

        assertEquals(ViewerUiState.DisplayMode.SEQUENTIAL, mode)
    }

    private fun MainActivity.invokeConfigureIntervalPicker(picker: NumberPicker, intervalSeconds: Int) {
        val method = MainActivity::class.java.getDeclaredMethod(
            "configureIntervalPicker",
            NumberPicker::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(this, picker, intervalSeconds)
    }

    private fun MainActivity.invokeCheckedIdForMode(
        binding: DialogSettingsBinding,
        mode: ViewerUiState.DisplayMode
    ): Int {
        val method = MainActivity::class.java.getDeclaredMethod(
            "checkedIdForMode",
            DialogSettingsBinding::class.java,
            ViewerUiState.DisplayMode::class.java
        )
        method.isAccessible = true
        return method.invoke(this, binding, mode) as Int
    }

    private fun MainActivity.invokeModeFromSelection(binding: DialogSettingsBinding): ViewerUiState.DisplayMode {
        val method = MainActivity::class.java.getDeclaredMethod(
            "modeFromSelection",
            DialogSettingsBinding::class.java
        )
        method.isAccessible = true
        return method.invoke(this, binding) as ViewerUiState.DisplayMode
    }
}
