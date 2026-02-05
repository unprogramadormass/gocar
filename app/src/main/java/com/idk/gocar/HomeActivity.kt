package com.example.gocar

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.io.FileOutputStream

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Aplicar tema guardado
        val prefs = getSharedPreferences("GoCarPrefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)

        setContentView(R.layout.activity_home)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        if (savedInstanceState == null) loadFragment(HomeFragment())

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_settings -> loadFragment(SettingsFragment())
            }
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).commit()
    }
}

class HomeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        view.findViewById<Button>(R.id.btnOpenDashboard).setOnClickListener {
            startActivity(Intent(activity, MainActivity::class.java))
        }
        return view
    }
}

class SettingsFragment : Fragment() {
    private lateinit var prefs: SharedPreferences
    private lateinit var ivSelectedAppIcon: ImageView
    private lateinit var tvSelectedAppName: TextView
    private lateinit var ivWallpaperPreview: ImageView

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { saveWallpaper(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        prefs = requireContext().getSharedPreferences("GoCarPrefs", Context.MODE_PRIVATE)

        ivSelectedAppIcon = view.findViewById(R.id.ivSelectedAppIcon)
        tvSelectedAppName = view.findViewById(R.id.tvSelectedAppName)
        ivWallpaperPreview = view.findViewById(R.id.ivWallpaperPreview)

        setupWallpaper(view)
        setupMusicSelection(view)
        setupOtherButtons(view)
        updateCurrentMusicUI()
        loadCurrentWallpaperPreview()
        return view
    }

    private fun setupWallpaper(view: View) {
        view.findViewById<Button>(R.id.btnChangeWallpaper).setOnClickListener { pickImage.launch("image/*") }
        view.findViewById<Button>(R.id.btnResetWallpaper).setOnClickListener {
            val file = File(requireContext().filesDir, "custom_wallpaper.jpg")
            if (file.exists()) file.delete()
            prefs.edit().putBoolean("has_custom_wallpaper", false).apply()
            loadCurrentWallpaperPreview()
            Toast.makeText(context, "Fondo restablecido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveWallpaper(sourceUri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(sourceUri)
            val file = File(requireContext().filesDir, "custom_wallpaper.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close(); outputStream.close()
            prefs.edit().putBoolean("has_custom_wallpaper", true).apply()
            loadCurrentWallpaperPreview()
            Toast.makeText(context, "Fondo actualizado", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT).show() }
    }

    private fun loadCurrentWallpaperPreview() {
        if (prefs.getBoolean("has_custom_wallpaper", false)) {
            val file = File(requireContext().filesDir, "custom_wallpaper.jpg")
            if (file.exists()) {
                ivWallpaperPreview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                ivWallpaperPreview.alpha = 1.0f
                return
            }
        }
        ivWallpaperPreview.setImageResource(R.drawable.fondocarplay)
        ivWallpaperPreview.alpha = 1.0f
    }

    private fun setupMusicSelection(view: View) {
        view.findViewById<Button>(R.id.btnDefaultMusic).setOnClickListener { showAppSelectionDialog() }
    }

    private fun updateCurrentMusicUI() {
        val pkg = prefs.getString("default_music_pkg", "internal")
        val pm = requireContext().packageManager
        if (pkg == "internal") {
            tvSelectedAppName.text = "Música Local (Gocar)"
            try { ivSelectedAppIcon.setImageResource(R.drawable.ic_menu_my_music) }
            catch (e: Exception) { ivSelectedAppIcon.setImageResource(android.R.drawable.ic_media_play) }
        } else {
            try {
                val appInfo = pm.getApplicationInfo(pkg!!, 0)
                tvSelectedAppName.text = pm.getApplicationLabel(appInfo)
                ivSelectedAppIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
            } catch (e: Exception) {
                tvSelectedAppName.text = "Desconocida"
                prefs.edit().putString("default_music_pkg", "internal").apply()
            }
        }
    }

    data class AppItem(val name: String, val icon: Drawable?, val packageName: String, val isInternal: Boolean)
    private fun showAppSelectionDialog() {
        val pm = requireContext().packageManager
        val appList = ArrayList<AppItem>()
        try { appList.add(AppItem("Música Local (Gocar)", ContextCompat.getDrawable(requireContext(), R.drawable.ic_menu_my_music), "internal", true)) }
        catch (e: Exception) { appList.add(AppItem("Música Local", ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_media_play), "internal", true)) }
        val pkgs = listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.apple.android.music", "com.amazon.mp3", "deezer.android.app", "com.huawei.music")
        for (p in pkgs) { try { val i = pm.getApplicationInfo(p, 0); appList.add(AppItem(pm.getApplicationLabel(i).toString(), pm.getApplicationIcon(i), p, false)) } catch (e: Exception) {} }

        val builder = AlertDialog.Builder(requireContext())
        val recycler = RecyclerView(requireContext()); recycler.layoutManager = LinearLayoutManager(requireContext())
        val adapter = AppAdapter(appList) { prefs.edit().putString("default_music_pkg", it.packageName).apply(); updateCurrentMusicUI() }
        recycler.adapter = adapter; recycler.setPadding(30, 30, 30, 30)
        val d = builder.setView(recycler).setTitle("Elige App por Defecto").setNegativeButton("Cancelar", null).create()
        adapter.dialogRef = d; d.show()
    }

    inner class AppAdapter(private val list: List<AppItem>, private val onClick: (AppItem) -> Unit) : RecyclerView.Adapter<AppAdapter.Holder>() {
        var dialogRef: AlertDialog? = null
        inner class Holder(v: View) : RecyclerView.ViewHolder(v) { val img: ImageView = v.findViewById(R.id.ivAppIcon); val txt: TextView = v.findViewById(R.id.tvAppName) }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = Holder(LayoutInflater.from(p.context).inflate(R.layout.item_app_selector, p, false))
        override fun onBindViewHolder(h: Holder, i: Int) { val x = list[i]; h.txt.text = x.name; if(x.icon!=null) h.img.setImageDrawable(x.icon); h.itemView.setOnClickListener { onClick(x); dialogRef?.dismiss() } }
        override fun getItemCount() = list.size
    }

    private fun setupOtherButtons(view: View) {
        view.findViewById<Button>(R.id.btnPermissions).setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", requireContext().packageName, null) }) }
        view.findViewById<Button>(R.id.btnContact).setOnClickListener { try { startActivity(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:soporte@gocar.com") }) } catch (e: Exception) { Toast.makeText(context, "No hay email", Toast.LENGTH_SHORT).show() } }
    }
}