package com.ras.autoclick

import java.text.Normalizer

object T {
    var es = true
    private val m = mapOf(
        "open" to ("ABRIR PANEL" to "OPEN PANEL"),
        "channels" to ("CANALES" to "CHANNELS"),
        "search" to ("BUSQUEDA ESPECIFICA" to "SPECIFIC SEARCH"),
        "searchinfo" to ("Subí hasta 3 capturas de cartas" to "Upload up to 3 card screenshots"),
        "add" to ("+ AGREGAR CAPTURA" to "+ ADD SCREENSHOT"),
        "confirm" to ("Nombre del jugador" to "Player name"),
        "back" to ("VOLVER" to "BACK"),
        "play" to ("▶ INICIAR" to "▶ START"),
        "pause" to ("⏸ PAUSAR" to "⏸ PAUSE"),
        "hide" to ("OCULTAR PUNTEROS" to "HIDE POINTERS"),
        "show" to ("MOSTRAR PUNTEROS" to "SHOW POINTERS"),
        "sellall" to ("SELL ALL" to "SELL ALL"),
        "selldup" to ("SELL DUPLO" to "SELL DUPLO"),
        "close" to ("✖ APAGAR Y CERRAR" to "✖ TURN OFF & CLOSE"),
        "packs" to ("Sobres" to "Packs"),
        "time" to ("Tiempo" to "Time"),
        "found" to ("HAZ CONSEGUIDO TU CARTA ESPECIFICA" to "YOU GOT YOUR SPECIFIC CARD"),
        "needacc" to ("Activá RAS// AUTO CLICK en Accesibilidad" to "Enable RAS// AUTO CLICK in Accessibility")
    )
    fun t(k: String) = m[k]!!.let { if (es) it.first else it.second }

    fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").uppercase()
        .replace(Regex("[^A-Z ]"), " ").replace(Regex("\\s+"), " ").trim()
}
