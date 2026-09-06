package rk.or.android

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import java.io.BufferedReader
import java.io.InputStreamReader
import rk.or.core.Command
import rk.or.core.Model

/**
 * Minimal Kotlin shell: a full-screen View3D plus a native popup menu (res/menu/menu_main.xml)
 * to pick one of the scripts bundled in res/raw. No crease-pattern editor, no command console,
 * no save/load -- see the Kotlin port session notes for what's deliberately out of scope.
 */
class MainActivity : Activity() {
    private val model = Model()
    private val command = Command(model)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val view3d = findViewById<View3D>(R.id.view3d)
        view3d.model = model
        view3d.command = command
        view3d.needRebuild = true

        findViewById<View>(R.id.menuButton).setOnClickListener { anchor -> showMenu(anchor, view3d) }

        loadScript(R.raw.test, view3d)
    }

    private fun showMenu(anchor: View, view3d: View3D) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            val id = when (item.itemId) {
                R.id.menu_test -> R.raw.test
                R.id.menu_plane -> R.raw.plane
                R.id.menu_cocotte -> R.raw.cocotte
                R.id.menu_reverse_inside_test -> R.raw.reverse_inside_test
                else -> return@setOnMenuItemClickListener false
            }
            loadScript(id, view3d)
            true
        }
        popup.show()
    }

    private fun loadScript(id: Int, view3d: View3D) {
        val text = readRawText(id) ?: return
        command.command(text)
        view3d.needRebuild = true
        view3d.requestRender()
    }

    private fun readRawText(id: Int): String? = try {
        resources.openRawResource(id).use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }
    } catch (e: Exception) {
        null
    }
}
