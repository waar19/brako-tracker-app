package com.brk718.tracker.data.remote

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory que mapea slugs de AfterShip a su scraper directo correspondiente.
 *
 * Se usa como fallback en [ShipmentRepository.refreshShipment] cuando AfterShip
 * devuelve datos vacíos o falla para un carrier colombiano sin CAPTCHA.
 *
 * Para añadir un nuevo scraper:
 *  1. Crear la clase `XxxScraper : CarrierScraper`
 *  2. Inyectarla en el constructor de esta factory
 *  3. Añadir la entrada en [scraperMap]
 */
@Singleton
class CarrierScraperFactory @Inject constructor(
    private val servientregaScraper: ServientregaScraper,
    private val coordinadoraScraper: CoordinadoraScraper,
    private val tccScraper: TccScraper,
    private val deprisaScraper: DeprisaScraper,
    private val enviaScraper: EnviaScraper,
    private val redpackScraper: RedpackScraper,
    private val estafetaScraper: EstafetaScraper,
) {
    private val scraperMap: Map<String, CarrierScraper> = mapOf(
        "servientrega" to servientregaScraper,
        "coordinadora" to coordinadoraScraper,
        "tcc-co"       to tccScraper,
        "deprisa"      to deprisaScraper,
        "envia-co"     to enviaScraper,
        "redpack"      to redpackScraper,
        "estafeta"     to estafetaScraper,
    )

    /**
     * Devuelve el scraper para el slug dado, o null si no hay scraper
     * disponible para ese carrier (e.g. Envía con CAPTCHA).
     */
    fun getFor(slug: String): CarrierScraper? = scraperMap[slug]
}
