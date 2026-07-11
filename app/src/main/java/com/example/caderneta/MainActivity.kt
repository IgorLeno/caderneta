package com.example.caderneta

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.caderneta.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setupSystemBars()
        setContentView(binding.root)
        applyWindowInsets()

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (BuildConfig.IS_AUDIT) {
            toolbar.subtitle = "AUDITORIA - dados ficticios"
        }

        val navHostFragment =
            supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Configurar destinos de nível superior
        appBarConfiguration =
            AppBarConfiguration(
                setOf(
                    R.id.vendasFragment,
                    R.id.consultasFragment,
                    R.id.balancoCaixaFragment,
                    R.id.historicoVendasFragment,
                    R.id.configuracoesFragment,
                ),
            )

        // Setup da ActionBar e BottomNavigation
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavigation.setupWithNavController(navController)

        // Monitorar mudanças de destino e manter visibilidade do menu
        navController.addOnDestinationChangedListener { _, destination, _ ->
            supportActionBar?.subtitle =
                if (BuildConfig.IS_AUDIT) "AUDITORIA - dados ficticios" else null
            binding.bottomNavigation.visibility =
                when (destination.id) {
                    R.id.vendasFragment,
                    R.id.consultasFragment,
                    R.id.balancoCaixaFragment,
                    R.id.historicoVendasFragment,
                    R.id.configuracoesFragment,
                    -> View.VISIBLE
                    else -> View.GONE
                }
        }

        // Listener simplificado para navegação do BottomNav
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            NavigationUI.onNavDestinationSelected(item, navController)
        }
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
    }

    private fun applyWindowInsets() {
        val toolbarInitialTop = binding.toolbar.paddingTop
        val toolbarInitialBottom = binding.toolbar.paddingBottom
        val bottomNavInitialTop = binding.bottomNavigation.paddingTop
        val bottomNavInitialBottom = binding.bottomNavigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft,
                toolbarInitialTop + bars.top,
                binding.toolbar.paddingRight,
                toolbarInitialBottom,
            )
            binding.bottomNavigation.setPadding(
                binding.bottomNavigation.paddingLeft,
                bottomNavInitialTop,
                binding.bottomNavigation.paddingRight,
                bottomNavInitialBottom + maxOf(bars.bottom, ime.bottom),
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
}
