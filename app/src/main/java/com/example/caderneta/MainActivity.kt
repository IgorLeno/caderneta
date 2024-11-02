package com.example.caderneta

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
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
        setContentView(binding.root)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Configurar destinos de nível superior
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.vendasFragment,
                R.id.consultasFragment,
                R.id.balancoCaixaFragment,
                R.id.historicoVendasFragment,
                R.id.configuracoesFragment
            )
        )

        // Setup da ActionBar e BottomNavigation
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavigation.setupWithNavController(navController)

        // Monitorar mudanças de destino e manter visibilidade do menu
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.visibility = when (destination.id) {
                R.id.vendasFragment,
                R.id.consultasFragment,
                R.id.balancoCaixaFragment,
                R.id.historicoVendasFragment,
                R.id.configuracoesFragment -> View.VISIBLE
                else -> View.GONE
            }
            Log.d("Navigation", "Navigated to destination: ${destination.label}")
        }

        // Listener simplificado para navegação do BottomNav
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            NavigationUI.onNavDestinationSelected(item, navController)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}