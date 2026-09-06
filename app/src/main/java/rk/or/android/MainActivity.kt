package rk.or.android

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import java.io.BufferedReader
import java.io.InputStreamReader
import rk.or.core.Command
import rk.or.core.Model

/**
 * Minimal Kotlin shell: a full-screen View3D plus a native navigation drawer (hamburger icon,
 * top-left) to pick one of the scripts bundled in res/raw. No crease-pattern editor, no command
 * console, no save/load -- see the Kotlin port session notes for what's deliberately out of scope.
 */
class MainActivity : Activity() {
    private val model = Model()
    private val command = Command(model)
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val view3d = findViewById<View3D>(R.id.view3d)
        view3d.model = model
        view3d.command = command
        view3d.needRebuild = true

        drawerLayout = findViewById(R.id.drawerLayout)

        // GLSurfaceView renders through its own Surface outside the normal view-hierarchy
        // canvas, which doesn't composite correctly with DrawerLayout's animated sliding drawer
        // on top of it (confirmed: the drawer renders perfectly with View3D hidden, and
        // corrupts -- wrong width, clipped content -- when View3D is visible underneath it).
        // Hiding it for the drawer's slide/open/closed lifetime sidesteps the conflict; there's
        // nothing to see through a full-height drawer anyway.
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                view3d.visibility = if (slideOffset > 0f) View.INVISIBLE else View.VISIBLE
            }

            override fun onDrawerClosed(drawerView: View) {
                view3d.visibility = View.VISIBLE
            }
        })

        val navView = findViewById<NavigationView>(R.id.navView)
        navView.setNavigationItemSelectedListener { item ->
            val id = when (item.itemId) {
                R.id.menu_test -> R.raw.test
                R.id.menu_plane -> R.raw.plane
                R.id.menu_cocotte -> R.raw.cocotte
                R.id.menu_reverse_inside_test -> R.raw.reverse_inside_test
                else -> return@setNavigationItemSelectedListener false
            }
            loadScript(id, view3d)
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // This app draws edge-to-edge (Android 15+ / API 35 enforces it): content extends behind
        // the status bar, but the status bar itself still intercepts touches in its own area, so
        // a fixed top margin would leave the button dead on devices with a taller status bar
        // (different density, a display cutout, ...). Push it down by the actual inset instead.
        val menuButton = findViewById<View>(R.id.menuButton)
        val menuButtonStartMargin = (menuButton.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        ViewCompat.setOnApplyWindowInsetsListener(menuButton) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarInset.top + menuButtonStartMargin
            }
            insets
        }
        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        loadScript(R.raw.test, view3d)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
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
