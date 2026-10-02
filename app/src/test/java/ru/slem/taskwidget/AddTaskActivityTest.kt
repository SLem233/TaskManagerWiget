package ru.slem.taskwidget

import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 36])
class AddTaskActivityTest {
    @Test fun dueDateCanBePickedAndCleared() {
        val activity = Robolectric.buildActivity(AddTaskActivity::class.java).setup().get()
        val pick = activity.findViewById<Button>(R.id.pick_due)
        pick.performClick()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            as android.app.DatePickerDialog
        dialog.datePicker.updateDate(2026, 9, 4)
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(pick.text.toString().contains("2026-10-04"))
        activity.findViewById<Button>(R.id.clear_due).performClick()
        assertTrue(pick.text.toString().contains("без даты"))
        activity.finish()
    }
    @Test fun plusTargetHasDedicatedFormWithTagsAndThreeDates() {
        val klass = Class.forName("ru.slem.taskwidget.AddTaskActivity").asSubclass(Activity::class.java)
        val activity = Robolectric.buildActivity(klass).setup().get()
        fun id(name: String): Int = activity.resources.getIdentifier(name, "id", activity.packageName)
        assertEquals("Новая задача", activity.findViewById<TextView>(id("add_heading")).text.toString())
        assertNotNull(activity.findViewById<EditText>(id("add_description")))
        assertEquals("#task", activity.findViewById<EditText>(id("add_tags")).text.toString())
        assertNotNull(activity.findViewById<Button>(id("pick_start")))
        assertNotNull(activity.findViewById<Button>(id("pick_scheduled")))
        assertNotNull(activity.findViewById<Button>(id("pick_due")))
        assertNotNull(activity.findViewById<Button>(id("save_task")))
        activity.finish()
    }
}