package com.seudominio.app_smart_companion.assistants

import com.seudominio.app_smart_companion.models.Assistant
import com.seudominio.app_smart_companion.R

/**
 * AssistantRegistry - Central registry for all available assistants
 * 
 * This singleton maintains the list of available assistants and their configurations.
 * To add a new assistant, simply add it to the AVAILABLE_ASSISTANTS list.
 * 
 * Example:
 * ```kotlin
 * val newAssistant = Assistant(
 *     id = "asst_YOUR_ASSISTANT_ID",
 *     name = "Your Assistant Name",
 *     description = "Brief description",
 *     menuResourceId = R.menu.your_assistant_menu,
 *     activeMenuResourceId = R.menu.your_assistant_active_menu
 * )
 * ```
 */
object AssistantRegistry {
    
    /**
     * List of all available assistants
     * Add new assistants here - they will automatically appear in the menu
     */
    val AVAILABLE_ASSISTANTS = listOf(
        Assistant(
            id = "asst_XQhJXBsG0JNgsBNzKqe7dQGa",
            name = "Coach SPIN",
            description = "Personal development coach focused on SPIN methodology",
            menuResourceId = R.menu.coach_spin_menu,
            activeMenuResourceId = R.menu.coach_active_menu
        )
        // Future assistants can be added here:
        // ,
        // Assistant(
        //     id = "asst_YOUR_NEW_ASSISTANT_ID",
        //     name = "Your Assistant Name",
        //     description = "Description of what this assistant does",
        //     menuResourceId = R.menu.your_assistant_menu,
        //     activeMenuResourceId = R.menu.your_assistant_active_menu
        // )
    )
    
    /**
     * Get assistant by ID
     */
    fun getAssistantById(id: String): Assistant? {
        return AVAILABLE_ASSISTANTS.find { it.id == id }
    }
    
    /**
     * Get assistant by name
     */
    fun getAssistantByName(name: String): Assistant? {
        return AVAILABLE_ASSISTANTS.find { it.name == name }
    }
}