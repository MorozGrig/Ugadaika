package ru.example.guessnumber

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import ru.example.guessnumber.databinding.ActivityMainBinding

/**
 * Главная (и единственная) активность приложения.
 * Содержит NavHostFragment для навигации между экранами (MVVM + Navigation Component).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получаем NavController из NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Привязываем ActionBar (кнопка "Назад") к навигационному графу
        setupActionBarWithNavController(navController)
    }

    /**
     * Обработка системной кнопки "Назад" через Navigation Component.
     */
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
