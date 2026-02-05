package com.example.gocar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// --- IMPORTS DE MAPBOX (V10/V11) ---
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.locationcomponent.location
// ESTE IMPORT ES VITAL PARA QUITAR LA ESCALA SIN CRASH
import com.mapbox.maps.plugin.scalebar.scalebar
import java.io.File

class MainActivity : AppCompatActivity() {

    data class AppItem(val name: String, val icon: Drawable?, val packageName: String, val isInternal: Boolean)

    // Modelo seguro para versiones nuevas de Android
    data class SongModel(val id: Long, val title: String, val artist: String)

    companion object {
        var mediaPlayer: MediaPlayer? = null
        var isLocalPlaybackActive = false
        var currentSongTitle = "Selecciona música"
        var currentSongArtist = "Artista"
        var currentSongId: Long = -1
        var currentSongIndex = -1
        var currentSongCover: Bitmap? = null
    }

    private var currentController: MediaController? = null
    private var currentCallback: MediaController.Callback? = null

    private lateinit var mapContainer: View
    private lateinit var contactsContainer: LinearLayout
    private lateinit var musicContainer: LinearLayout
    private lateinit var rightPanel: LinearLayout
    private lateinit var mapView: MapView

    private lateinit var btnMapas: ImageView
    private lateinit var btnTelefono: ImageView
    private lateinit var btnMusica: ImageView
    private lateinit var gridMenuContainer: LinearLayout
    private lateinit var appGridContainer: LinearLayout
    private lateinit var ivAppCalendar: ImageView
    private lateinit var ivAppMessages: ImageView
    private lateinit var ivAppWeather: ImageView
    private lateinit var ivAppMusicGrid: ImageView
    private lateinit var ivAppSettings: ImageView
    private lateinit var btnAppCustom: LinearLayout
    private lateinit var ivAppCustom: ImageView
    private lateinit var tvAppCustom: TextView
    private lateinit var ivNowPlayingCover: ImageView
    private lateinit var tvNowPlayingTitle: TextView
    private lateinit var tvNowPlayingArtist: TextView
    private lateinit var btnPrev: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var notificationIconsContainer: LinearLayout
    private lateinit var rvContacts: RecyclerView
    private lateinit var etSearch: EditText
    private val contactsList = ArrayList<ContactModel>()
    private val contactsAdapter = ContactsAdapter(contactsList)
    private lateinit var rvMusic: RecyclerView
    private val songList = ArrayList<SongModel>()
    private val musicAdapter = MusicAdapter(songList)
    private lateinit var tvBattery: TextView
    private lateinit var ivBattery: ImageView
    private lateinit var ivNetwork: ImageView
    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var prefs: SharedPreferences

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                val packages = intent?.getStringArrayListExtra("active_packages") ?: return
                notificationIconsContainer.removeAllViews()
                val uniquePackages = packages.distinct()
                for (pkg in uniquePackages) {
                    if (esAppImportante(pkg)) {
                        try {
                            val icon = packageManager.getApplicationIcon(pkg)
                            val imageView = ImageView(this@MainActivity)
                            val params = LinearLayout.LayoutParams(60, 60)
                            params.setMargins(8, 0, 8, 0)
                            imageView.layoutParams = params
                            imageView.setImageDrawable(icon)
                            notificationIconsContainer.addView(imageView)
                        } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun esAppImportante(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("whatsapp") || p.contains("facebook") || p.contains("instagram") ||
                p.contains("messaging") || p.contains("telegram") || p.contains("gm") || p.contains("twitter")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Modo inmersivo
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("GoCarPrefs", Context.MODE_PRIVATE)

        if (prefs.getBoolean("has_custom_wallpaper", false)) {
            val file = File(filesDir, "custom_wallpaper.jpg")
            if (file.exists()) {
                val root = findViewById<ConstraintLayout>(R.id.rootLayout)
                if (root != null) root.background = BitmapDrawable(resources, BitmapFactory.decodeFile(file.absolutePath))
            }
        }

        initViews()
        setupButtons()
        setupGridApps()
        setupSystemMonitors()

        // Iniciar mapa
        iniciarMapbox()

        rvContacts.layoutManager = LinearLayoutManager(this); rvContacts.adapter = contactsAdapter
        rvMusic.layoutManager = LinearLayoutManager(this); rvMusic.adapter = musicAdapter

        LocalBroadcastManager.getInstance(this).registerReceiver(notificationReceiver, IntentFilter("com.example.gocar.NOTIFICATION_LIST_UPDATE"))
        checkNotificationPermission()

        // Solicitar permisos en tiempo de ejecución (vital para Android 11+)
        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, 300)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermission(Manifest.permission.POST_NOTIFICATIONS, 301)
        }
    }

    private fun iniciarMapbox() {
        mapView.getMapboxMap().loadStyle(Style.DARK) {
            // 1. QUITAR LA BARRA DE ESCALA (Requiere el import scalebar)
            mapView.scalebar.enabled = false

            // 2. ACTIVAR UBICACIÓN (Punto Azul)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                initLocationComponent()
            }

            // Posición inicial
            val initialPosition = CameraOptions.Builder()
                .center(Point.fromLngLat(-99.1332, 19.4326))
                .zoom(15.0)
                .build()
            mapView.getMapboxMap().setCamera(initialPosition)
        }
    }

    private fun initLocationComponent() {
        val locationComponentPlugin = mapView.location
        locationComponentPlugin.updateSettings {
            this.enabled = true
            // Usamos el punto por defecto de Mapbox (círculo azul pulsante)
            this.locationPuck = LocationPuck2D()
        }
    }

    // --- PERMISOS COMPATIBLES CON ANDROID 11 - 16 ---
    private fun getMusicPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO // Android 13+
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE // Android 12 e inferior
        }
    }

    private fun checkPermission(p: String, c: Int) {
        if(ContextCompat.checkSelfPermission(this,p)!=PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,arrayOf(p),c)
        else {
            if(c==100) loadContacts()
            if(c==200) loadMusic()
            if(c==300) initLocationComponent() // Activar location si ya hay permiso
        }
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r,p,g)
        if(g.isNotEmpty() && g[0]==PackageManager.PERMISSION_GRANTED) {
            if(r==100) loadContacts()
            if(r==200) loadMusic()
            if(r==300) initLocationComponent() // Activar location al dar permiso
        }
    }

    override fun onResume() {
        super.onResume()
        updateSidebarIcons()
        updateCustomAppIcon()
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("com.example.gocar.ACTION_REQUEST_NOTIFICATIONS"))
        if (isLocalPlaybackActive) {
            tvNowPlayingTitle.text = currentSongTitle
            tvNowPlayingArtist.text = currentSongArtist
            ivNowPlayingCover.setImageBitmap(currentSongCover ?: BitmapFactory.decodeResource(resources, android.R.drawable.ic_menu_gallery))
            updatePlayPauseButtonState(mediaPlayer?.isPlaying == true)
        } else {
            syncWithExternalMusic()
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onStop() { super.onStop(); mapView.onStop() }

    override fun onDestroy() {
        mapView.onDestroy()
        currentController?.unregisterCallback(currentCallback!!)
        currentController = null
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver)
        super.onDestroy()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        mapContainer = mapView
        contactsContainer = findViewById(R.id.contactsContainer)
        musicContainer = findViewById(R.id.musicContainer)
        rightPanel = findViewById(R.id.rightPanel)
        btnMapas = findViewById(R.id.btnMapas)
        btnTelefono = findViewById(R.id.btnTelefono)
        btnMusica = findViewById(R.id.btnMusica)
        gridMenuContainer = findViewById(R.id.gridMenuContainer)
        appGridContainer = findViewById(R.id.appGridContainer)
        ivAppCalendar = findViewById(R.id.ivAppCalendar)
        ivAppMessages = findViewById(R.id.ivAppMessages)
        ivAppWeather = findViewById(R.id.ivAppWeather)
        ivAppMusicGrid = findViewById(R.id.ivAppMusicGrid)
        ivAppSettings = findViewById(R.id.ivAppSettings)
        btnAppCustom = findViewById(R.id.btnAppCustom)
        ivAppCustom = findViewById(R.id.ivAppCustom)
        tvAppCustom = findViewById(R.id.tvAppCustom)
        ivNowPlayingCover = findViewById(R.id.ivNowPlayingCover)
        tvNowPlayingTitle = findViewById(R.id.tvNowPlayingTitle)
        tvNowPlayingArtist = findViewById(R.id.tvNowPlayingArtist)
        btnPrev = findViewById(R.id.btnPrev); btnPlayPause = findViewById(R.id.btnPlayPause); btnNext = findViewById(R.id.btnNext)
        rvContacts = findViewById(R.id.rvContacts); etSearch = findViewById(R.id.etSearch); rvMusic = findViewById(R.id.rvMusic)
        tvBattery = findViewById(R.id.tvBattery); ivBattery = findViewById(R.id.ivBattery); ivNetwork = findViewById(R.id.ivNetwork)
        notificationIconsContainer = findViewById(R.id.notificationIconsContainer)
    }

    private fun updateSidebarIcons() {
        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
        cargarIconoRealEnBoton(btnMapas, mapIntent, android.R.drawable.ic_dialog_map)
        val telIntent = Intent(Intent.ACTION_DIAL)
        cargarIconoRealEnBoton(btnTelefono, telIntent, android.R.drawable.sym_action_call)
        val pkg = prefs.getString("default_music_pkg", "internal")
        if (pkg == "internal") {
            try { btnMusica.setImageResource(R.drawable.ic_menu_my_music) } catch (e: Exception) { btnMusica.setImageResource(android.R.drawable.ic_media_play) }
        } else {
            try { btnMusica.setImageDrawable(packageManager.getApplicationIcon(pkg!!)) } catch (e: Exception) { btnMusica.setImageResource(android.R.drawable.ic_media_play) }
        }
    }

    private fun cargarIconoRealEnBoton(imgView: ImageView, intent: Intent, defaultRes: Int) {
        val pm = packageManager
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo != null) imgView.setImageDrawable(resolveInfo.loadIcon(pm))
        else imgView.setImageResource(defaultRes)
    }

    private fun setupButtons() {
        btnMapas.setOnClickListener { showTab("MAPA") }
        btnTelefono.setOnClickListener { showTab("CONTACTOS"); checkPermission(Manifest.permission.READ_CONTACTS, 100) }
        btnMusica.setOnClickListener {
            val pkg = prefs.getString("default_music_pkg", "internal")
            if (pkg == "internal") {
                showTab("MUSICA")
                checkPermission(getMusicPermission(), 200)
            } else {
                try { val i = packageManager.getLaunchIntentForPackage(pkg!!); if(i!=null) startActivity(i) else showTab("MUSICA") } catch(e:Exception){ showTab("MUSICA") }
            }
        }
        gridMenuContainer.setOnClickListener { toggleAppGrid(true) }
        findViewById<LinearLayout>(R.id.btnAppExit).setOnClickListener { finish() }
        btnPlayPause.setOnClickListener { if (isLocalPlaybackActive) toggleLocalPlayPause() else sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        btnNext.setOnClickListener { if (isLocalPlaybackActive) playNextLocal() else sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT) }
        btnPrev.setOnClickListener { if (isLocalPlaybackActive) playPrevLocal() else sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        etSearch.addTextChangedListener(object : TextWatcher { override fun afterTextChanged(s: Editable?) { filterContacts(s.toString()) }; override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {} })
    }

    private fun toggleAppGrid(showGrid: Boolean) {
        if (showGrid) {
            mapView.visibility = View.GONE
            contactsContainer.visibility = View.GONE
            musicContainer.visibility = View.GONE
            rightPanel.visibility = View.GONE
            appGridContainer.visibility = View.VISIBLE
        } else {
            appGridContainer.visibility = View.GONE
            rightPanel.visibility = View.VISIBLE
            showTab("MAPA")
        }
    }

    private fun setupGridApps() {
        setupSingleApp(findViewById(R.id.btnAppCalendar), ivAppCalendar, "com.google.android.calendar", "com.android.calendar")
        setupSingleApp(findViewById(R.id.btnAppMessages), ivAppMessages, "com.google.android.apps.messaging", "com.android.mms")
        setupSingleApp(findViewById(R.id.btnAppWeather), ivAppWeather, "com.google.android.googlequicksearchbox", "com.sec.android.daemonapp")
        val defaultMusicPkg = prefs.getString("default_music_pkg", "internal")
        if (defaultMusicPkg != "internal") {
            setupSingleApp(findViewById(R.id.btnAppMusicGrid), ivAppMusicGrid, defaultMusicPkg!!, null)
        } else {
            findViewById<LinearLayout>(R.id.btnAppMusicGrid).setOnClickListener {
                toggleAppGrid(false)
                showTab("MUSICA")
                checkPermission(getMusicPermission(), 200)
            }
        }
        setupSingleApp(findViewById(R.id.btnAppSettings), ivAppSettings, "com.android.settings", null)
        btnAppCustom.setOnClickListener {
            val savedPkg = prefs.getString("custom_app_1_pkg", null)
            if (savedPkg == null) showAllAppsDialog()
            else {
                try {
                    val intent = packageManager.getLaunchIntentForPackage(savedPkg)
                    if (intent != null) startActivity(intent) else showAllAppsDialog()
                } catch (e: Exception) { showAllAppsDialog() }
            }
        }
        btnAppCustom.setOnLongClickListener { showAllAppsDialog(); true }
    }

    private fun updateCustomAppIcon() {
        val savedPkg = prefs.getString("custom_app_1_pkg", null)
        if (savedPkg != null) {
            try {
                ivAppCustom.setImageDrawable(packageManager.getApplicationIcon(savedPkg))
                ivAppCustom.setBackgroundResource(0)
                tvAppCustom.text = packageManager.getApplicationLabel(packageManager.getApplicationInfo(savedPkg, 0)).toString()
            } catch (e: Exception) {
                ivAppCustom.setImageResource(android.R.drawable.ic_input_add)
                tvAppCustom.text = "Añadir"
            }
        }
    }

    private fun showAllAppsDialog() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val allApps = pm.queryIntentActivities(mainIntent, 0)
        val items = ArrayList<AppItem>()
        for (resolveInfo in allApps) {
            items.add(AppItem(resolveInfo.loadLabel(pm).toString(), resolveInfo.loadIcon(pm), resolveInfo.activityInfo.packageName, false))
        }
        items.sortBy { it.name }
        val builder = AlertDialog.Builder(this)
        val recycler = RecyclerView(this); recycler.layoutManager = LinearLayoutManager(this)
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class H(v: View) : RecyclerView.ViewHolder(v) { val i: ImageView=v.findViewById(R.id.ivAppIcon); val t: TextView=v.findViewById(R.id.tvAppName) }
            override fun onCreateViewHolder(p: ViewGroup, t: Int) = H(LayoutInflater.from(p.context).inflate(R.layout.item_app_selector, p, false))
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
                val item = items[i]; (h as H).t.text = item.name; h.i.setImageDrawable(item.icon)
                h.itemView.setOnClickListener {
                    prefs.edit().putString("custom_app_1_pkg", item.packageName).apply()
                    updateCustomAppIcon(); dialogRef?.dismiss()
                }
            }
            override fun getItemCount() = items.size
            var dialogRef: AlertDialog? = null
        }
        recycler.adapter = adapter
        val dialog = builder.setView(recycler).setTitle("Selecciona una App").setNegativeButton("Cancelar", null).create()
        adapter.dialogRef = dialog
        dialog.show()
    }

    private fun setupSingleApp(btn: View, iv: ImageView, pkg1: String, pkg2: String?) {
        val pm = packageManager
        var pkgToUse: String? = null
        try { pm.getApplicationInfo(pkg1, 0); pkgToUse = pkg1 } catch (e: Exception) {
            if (pkg2 != null) try { pm.getApplicationInfo(pkg2, 0); pkgToUse = pkg2 } catch (e2: Exception) {}
        }
        if (pkgToUse != null) {
            iv.setImageDrawable(pm.getApplicationIcon(pkgToUse))
            btn.setOnClickListener { startActivity(pm.getLaunchIntentForPackage(pkgToUse!!)) }
        }
    }

    private fun showTab(tab: String) {
        appGridContainer.visibility = View.GONE
        rightPanel.visibility = View.VISIBLE
        mapView.visibility = if(tab=="MAPA") View.VISIBLE else View.GONE
        contactsContainer.visibility = if(tab=="CONTACTOS") View.VISIBLE else View.GONE
        musicContainer.visibility = if(tab=="MUSICA") View.VISIBLE else View.GONE
    }

    private fun syncWithExternalMusic() {
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            val component = ComponentName(this, MusicService::class.java)
            val controllers = mediaSessionManager.getActiveSessions(component)
            if (controllers.isNotEmpty()) registerMediaCallback(controllers[0])
            mediaSessionManager.addOnActiveSessionsChangedListener({ if(!it.isNullOrEmpty()) registerMediaCallback(it[0]) }, component)
        } catch (e: Exception) {}
    }

    private fun registerMediaCallback(newController: MediaController) {
        currentController?.unregisterCallback(currentCallback!!)
        currentController = newController
        currentCallback = object : MediaController.Callback() {
            override fun onMetadataChanged(m: android.media.MediaMetadata?) { updateMediaInfo(newController) }
            override fun onPlaybackStateChanged(s: PlaybackState?) { updateMediaInfo(newController) }
        }
        newController.registerCallback(currentCallback!!)
        updateMediaInfo(newController)
    }

    private fun updateMediaInfo(controller: MediaController?) {
        if (isLocalPlaybackActive || controller == null) return
        val m = controller.metadata
        if (m != null) {
            tvNowPlayingTitle.text = m.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Streaming"
            tvNowPlayingArtist.text = m.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val b = m.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
            if (b != null) ivNowPlayingCover.setImageBitmap(b) else ivNowPlayingCover.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        updatePlayPauseButtonState(controller.playbackState?.state == PlaybackState.STATE_PLAYING)
    }

    private fun updatePlayPauseButtonState(isPlaying: Boolean) { btnPlayPause.setImageResource(if(isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play) }

    // --- CARGA DE MÚSICA SEGURA (ANDROID 11+) ---
    private fun loadMusic() {
        songList.clear()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC}!=0",
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)

                if (idCol >= 0 && titleCol >= 0) {
                    while(cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol) ?: "Desconocido"
                        val artist = cursor.getString(artistCol) ?: "Desconocido"
                        songList.add(SongModel(id, title, artist))
                    }
                }
            }
            musicAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            Toast.makeText(this, "Error cargando música: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- REPRODUCCIÓN SEGURA (ANDROID 11+) ---
    private fun playLocalSong(index: Int) {
        val s = songList[index]
        currentSongIndex = index
        currentSongTitle = s.title
        currentSongArtist = s.artist
        currentSongId = s.id
        isLocalPlaybackActive = true

        try {
            if (mediaPlayer == null) mediaPlayer = MediaPlayer()
            mediaPlayer?.reset()

            val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, s.id)

            mediaPlayer?.setDataSource(applicationContext, contentUri)
            mediaPlayer?.prepare()
            mediaPlayer?.start()

            tvNowPlayingTitle.text = s.title
            tvNowPlayingArtist.text = s.artist
            updatePlayPauseButtonState(true)

            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(applicationContext, contentUri)
                val art = r.embeddedPicture
                ivNowPlayingCover.setImageBitmap(if(art!=null) BitmapFactory.decodeByteArray(art, 0, art.size) else null)
            } catch(e: Exception) {}

        } catch(e: Exception) {
            Toast.makeText(this, "Error al reproducir", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleLocalPlayPause() { mediaPlayer?.let { if(it.isPlaying) { it.pause(); updatePlayPauseButtonState(false) } else { it.start(); updatePlayPauseButtonState(true) } } }
    private fun playNextLocal() { if(songList.isNotEmpty()) playLocalSong((currentSongIndex+1)%songList.size) }
    private fun playPrevLocal() { if(songList.isNotEmpty()) playLocalSong(if(currentSongIndex-1<0)songList.size-1 else currentSongIndex-1) }

    private fun checkNotificationPermission() {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if(enabled!=null && enabled.contains(packageName)) syncWithExternalMusic()
    }

    private fun sendMediaKey(kc: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, kc))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, kc))
    }

    private fun loadContacts() {
        contactsList.clear()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
            contentResolver.query(uri, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (nameIndex >= 0 && numberIndex >= 0) {
                    while (cursor.moveToNext()) {
                        contactsList.add(ContactModel(cursor.getString(nameIndex) ?: "Desconocido", cursor.getString(numberIndex) ?: ""))
                    }
                }
            }
            contactsAdapter.updateList(contactsList)
        } catch (e: Exception) {}
    }

    private fun filterContacts(q: String) { contactsAdapter.updateList(ArrayList(contactsList.filter{it.name.contains(q,true)})) }

    private fun setupSystemMonitors() {
        registerReceiver(object:BroadcastReceiver(){
            override fun onReceive(c:Context?,i:Intent?){
                val l=i?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: 0
                val s=i?.getIntExtra(BatteryManager.EXTRA_SCALE,-1) ?: 100
                tvBattery.text="${(l*100)/s}%"
                val st=i?.getIntExtra(BatteryManager.EXTRA_STATUS,-1) ?: -1
                ivBattery.setImageResource(if(st==BatteryManager.BATTERY_STATUS_CHARGING) android.R.drawable.ic_lock_idle_charging else R.drawable.ic_battery_full)
            }
        }, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).registerDefaultNetworkCallback(object:ConnectivityManager.NetworkCallback(){
            override fun onAvailable(n:android.net.Network){ runOnUiThread{ivNetwork.visibility=View.VISIBLE} }
            override fun onLost(n:android.net.Network){ runOnUiThread{ivNetwork.visibility=View.GONE} }
        })
    }

    data class ContactModel(val name: String, val number: String)
    inner class ContactsAdapter(private var list: ArrayList<ContactModel>):RecyclerView.Adapter<ContactsAdapter.ViewHolder>(){
        fun updateList(n:ArrayList<ContactModel>){list=n;notifyDataSetChanged()}
        inner class ViewHolder(v:View):RecyclerView.ViewHolder(v){val t:TextView=v.findViewById(R.id.tvContactName);val c:ImageView=v.findViewById(R.id.btnCall);val s:ImageView=v.findViewById(R.id.btnSms)}
        override fun onCreateViewHolder(p:ViewGroup,t:Int)=ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_contact,p,false))
        override fun onBindViewHolder(h:ViewHolder,i:Int){val c=list[i];h.t.text=c.name;h.c.setOnClickListener{startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:${c.number}")))};h.s.setOnClickListener{startActivity(Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:${c.number}")))}}
        override fun getItemCount()=list.size
    }

    inner class MusicAdapter(private val list: ArrayList<SongModel>):RecyclerView.Adapter<MusicAdapter.ViewHolder>(){
        inner class ViewHolder(v:View):RecyclerView.ViewHolder(v){val t:TextView=v.findViewById(R.id.tvSongTitle);val a:TextView=v.findViewById(R.id.tvSongArtist)}
        override fun onCreateViewHolder(p:ViewGroup,t:Int)=ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_song,p,false))
        override fun onBindViewHolder(h:ViewHolder,i:Int){val s=list[i];h.t.text=s.title;h.a.text=s.artist;h.itemView.setOnClickListener{playLocalSong(i)}}
        override fun getItemCount()=list.size
    }
}