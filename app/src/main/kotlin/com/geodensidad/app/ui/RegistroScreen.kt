package com.geodensidad.app.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geodensidad.app.AppContainer
import com.geodensidad.app.data.Densidad
import com.geodensidad.app.data.Registro
import com.geodensidad.app.data.calcular
import com.geodensidad.app.data.conDensidades
import com.geodensidad.app.data.densidades
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import com.geodensidad.core.calc.LegacyResult
import com.geodensidad.core.calc.Severity
import com.geodensidad.core.number.DecimalText
import com.geodensidad.core.number.Fmt
import com.geodensidad.core.number.ParsedDecimal
import com.geodensidad.core.report.PlanillaBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

class RegistroViewModel(private val c: AppContainer, h: SavedStateHandle) : ViewModel() {
    private val id: String = h["id"]!!
    val registro = MutableStateFlow<Registro?>(null)
    /** Texto tal como lo escribe el usuario en cada campo numérico (clave "i", "q", "r" o "<n>.<campo>"). */
    val textos = MutableStateFlow<Map<String, String>>(emptyMap())
    val seleccion = MutableStateFlow(0)
    val guardado = MutableStateFlow("")
    val mensaje = MutableStateFlow<String?>(null)
    val generando = MutableStateFlow(false)
    private var job: Job? = null
    private var pendiente = false

    init {
        viewModelScope.launch {
            val r = c.db.registros().obtener(id) ?: return@launch
            registro.value = r
            val t = mutableMapOf("i" to DecimalText.storedToEditText(r.i), "q" to DecimalText.storedToEditText(r.q), "r" to DecimalText.fractionToPercentText(r.r))
            r.densidades.forEachIndexed { n, d -> t.putAll(textosDe(n, d)) }
            textos.value = t
        }
    }

    private fun textosDe(n: Int, d: Densidad) = mapOf(
        "$n.a" to DecimalText.storedToEditText(d.a), "$n.b" to DecimalText.storedToEditText(d.b), "$n.d" to DecimalText.storedToEditText(d.d),
        "$n.f" to DecimalText.storedToEditText(d.f), "$n.h" to DecimalText.storedToEditText(d.h), "$n.j" to DecimalText.storedToEditText(d.j),
        "$n.l" to DecimalText.storedToEditText(d.l),
    )

    fun editar(t: (Registro) -> Registro) {
        val r = registro.value ?: return
        registro.value = t(r).copy(modificado = System.currentTimeMillis())
        pendiente = true; guardado.value = "Guardando…"
        job?.cancel()
        job = viewModelScope.launch { delay(500); guardar() }
    }

    private suspend fun guardar() {
        val r = registro.value ?: return
        c.db.registros().guardar(r); pendiente = false
        guardado.value = "Guardado"
    }

    fun editarDensidad(n: Int, t: (Densidad) -> Densidad) = editar { r -> r.conDensidades(r.densidades.mapIndexed { i, d -> if (i == n) t(d) else d }) }

    /** Número digitado: vacío o inválido = dato faltante (nunca cero). L y R en decimal, como en el Excel. */
    private fun canon(raw: String, campo: String): String? {
        val p = if (campo == "r") DecimalText.percentTextToFraction(raw) else DecimalText.parse(raw)
        return (p as? ParsedDecimal.Valid)?.let { DecimalText.canonical(it.value) }
    }

    /** Orden de digitación igual a las filas del Excel. I, Q y R son comunes a todas las densidades. */
    val orden = listOf("a", "b", "d", "f", "h", "i", "j", "l", "q", "r")
    private val comunes = setOf("i", "q", "r")
    fun texto(n: Int, campo: String) = textos.value[if (campo in comunes) campo else "$n.$campo"] ?: ""
    fun escribir(n: Int, campo: String, raw: String) = if (campo in comunes) numeroComun(campo, raw) else numeroDensidad(n, campo, raw)

    fun numeroComun(campo: String, raw: String) {
        textos.value = textos.value + (campo to raw)
        val v = canon(raw, campo)
        editar { when (campo) { "i" -> it.copy(i = v); "q" -> it.copy(q = v); else -> it.copy(r = v) } }
    }

    fun numeroDensidad(n: Int, campo: String, raw: String) {
        textos.value = textos.value + ("$n.$campo" to raw)
        val v = canon(raw, campo)
        editarDensidad(n) { d ->
            when (campo) { "a" -> d.copy(a = v); "b" -> d.copy(b = v); "d" -> d.copy(d = v); "f" -> d.copy(f = v); "h" -> d.copy(h = v); "j" -> d.copy(j = v); else -> d.copy(l = v) }
        }
    }

    /** Nueva densidad: copia D y F de la anterior, como hace el Excel (=+C18, =+C20). */
    fun agregarDensidad() {
        val r = registro.value ?: return
        val lista = r.densidades
        if (lista.size >= Registro.MAX_DENSIDADES) { mensaje.value = "El formato admite hasta ${Registro.MAX_DENSIDADES} densidades"; return }
        val previa = lista.lastOrNull()
        val nueva = Densidad(d = previa?.d, f = previa?.f, capa = previa?.capa ?: "", espesor = previa?.espesor ?: "", costado = "")
        textos.value = textos.value + textosDe(lista.size, nueva)
        editar { it.conDensidades(lista + nueva) }
        seleccion.value = lista.size
    }

    fun eliminarDensidad(n: Int) {
        val r = registro.value ?: return
        val lista = r.densidades.toMutableList().apply { removeAt(n) }
        val t = textos.value.filterKeys { !it.contains('.') }.toMutableMap()
        lista.forEachIndexed { i, d -> t.putAll(textosDe(i, d)) }
        textos.value = t
        editar { it.conDensidades(lista) }
        seleccion.value = (n - 1).coerceAtLeast(0)
    }

    fun excel(compartir: (File) -> Unit) {
        val r = registro.value ?: return
        viewModelScope.launch {
            job?.cancel(); guardar()
            runCatching { c.intercambio.archivo(listOf(r), "Registro-${r.registro.ifBlank { "SN" }}-${r.fecha}.xlsx") }
                .onSuccess(compartir).onFailure { mensaje.value = "No se exportó: ${it.message}" }
        }
    }

    fun pdf(abrir: (File) -> Unit) {
        val r = registro.value ?: return
        if (generando.value) return
        generando.value = true
        viewModelScope.launch {
            job?.cancel(); guardar()
            runCatching { c.pdf.generar(r) }.onSuccess(abrir).onFailure { mensaje.value = "No se generó el PDF: ${it.message}" }
            generando.value = false
        }
    }

    override fun onCleared() {
        val r = registro.value
        if (pendiente && r != null) c.appScope.launch { c.db.registros().guardar(r) }
        super.onCleared()
    }
}

@Composable
fun RegistroScreen(volver: () -> Unit) {
    val vm = appViewModel { c, h -> RegistroViewModel(c, h) }
    val reg by vm.registro.collectAsStateWithLifecycle()
    val textos by vm.textos.collectAsStateWithLifecycle()
    val sel by vm.seleccion.collectAsStateWithLifecycle()
    val guardado by vm.guardado.collectAsStateWithLifecycle()
    val generando by vm.generando.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    ErrorSnack(vm.mensaje.collectAsStateWithLifecycle().value, { vm.mensaje.value = null }, snack)
    var borrar by remember { mutableStateOf<Int?>(null) }
    /** Campo numérico activo: "<densidad>.<campo>" (p. ej. "0.a"); null = teclado propio oculto. */
    var activo by rememberSaveable { mutableStateOf<String?>(null) }
    val teclado = LocalSoftwareKeyboardController.current
    val foco = LocalFocusManager.current
    LaunchedEffect(activo) { if (activo != null) { foco.clearFocus(); teclado?.hide() } }
    BackHandler(enabled = activo != null) { activo = null }
    val abrirPdf: (File) -> Unit = { f ->
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val ver = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val compartir = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { ctx.startActivity(Intent.createChooser(ver, "Abrir PDF").putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(compartir))) }
        catch (e: ActivityNotFoundException) { ctx.startActivity(Intent.createChooser(compartir, "Compartir PDF")) }
    }

    Scaffold(
        topBar = {
            AppBar("Registro ${reg?.registro?.ifBlank { null } ?: ""}".trim(), guardado.ifBlank { null }, volver) {
                IconButton(onClick = { vm.excel { f ->
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartir Excel"))
                } }) { Icon(Icons.Default.TableView, "Exportar a Excel") }
                if (generando) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                else IconButton(onClick = { vm.pdf(abrirPdf) }) { Icon(Icons.Default.PictureAsPdf, "Generar PDF") }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            val a = activo
            val r0 = reg
            if (a != null && r0 != null) {
                val n0 = a.substringBefore('.').toInt(); val campo = a.substringAfter('.')
                val i = vm.orden.indexOf(campo)
                val total = r0.densidades.size
                fun ir(nn: Int, cc: String) { vm.seleccion.value = nn; activo = "$nn.$cc" }
                val t = vm.texto(n0, campo)
                TecladoNumerico(
                    titulo = "Densidad ${n0 + 1} · ${campo.uppercase()} · ${NOMBRES[campo]}",
                    onTecla = { ch ->
                        val nuevo = when {
                            ch == ',' && t.contains(',') -> t
                            ch == ',' && t.isEmpty() -> "0,"
                            t.length >= 12 -> t
                            else -> t + ch
                        }
                        vm.escribir(n0, campo, nuevo)
                    },
                    onBorrar = { vm.escribir(n0, campo, t.dropLast(1)) }, onLimpiar = { vm.escribir(n0, campo, "") },
                    onAnterior = { if (i > 0) ir(n0, vm.orden[i - 1]) else if (n0 > 0) ir(n0 - 1, vm.orden.last()) },
                    onSiguiente = { if (i < vm.orden.lastIndex) ir(n0, vm.orden[i + 1]) else if (n0 + 1 < total) ir(n0 + 1, vm.orden.first()) },
                    onListo = { activo = null },
                    hayAnterior = i > 0 || n0 > 0, haySiguiente = i < vm.orden.lastIndex || n0 + 1 < total,
                )
            }
        },
    ) { pad ->
        val r = reg ?: return@Scaffold
        val dens = r.densidades
        val n = sel.coerceIn(0, (dens.size - 1).coerceAtLeast(0))
        val alEnfocarTexto = { activo = null }
        CompositionLocalProvider(LocalAlEnfocarTexto provides alEnfocarTexto) {
        Box(Modifier.padding(pad).fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Bloque("Encabezado") {
                    Campo("Proyecto", r.proyecto) { v -> vm.editar { it.copy(proyecto = v) } }
                    Campo("Localización", r.localizacion) { v -> vm.editar { it.copy(localizacion = v) } }
                    Campo("Procedencia", r.procedencia) { v -> vm.editar { it.copy(procedencia = v) } }
                    Par({ Campo("PR inicial", r.prInicial) { v -> vm.editar { it.copy(prInicial = v) } } }, { Campo("PR final", r.prFinal) { v -> vm.editar { it.copy(prFinal = v) } } })
                    Par({ Campo("Lado", r.lado) { v -> vm.editar { it.copy(lado = v) } } }, { Campo("Fecha", r.fecha) { v -> vm.editar { it.copy(fecha = v) } } })
                    Campo("Parte del relleno", r.parteRelleno) { v -> vm.editar { it.copy(parteRelleno = v) } }
                    Par({ Campo("Lote", r.lote) { v -> vm.editar { it.copy(lote = v) } } }, { Campo("Registro", r.registro) { v -> vm.editar { it.copy(registro = v) } } })
                }
            }
            item {
                Text("Densidades", style = MaterialTheme.typography.titleMedium, color = Condor.NavyDeep, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(dens) { i, _ -> FilterChip(i == n, { vm.seleccion.value = i; activo = null }, { Text("${i + 1}") }) }
                    item { AssistChip({ vm.agregarDensidad() }, { Text("Agregar") }, leadingIcon = { Icon(Icons.Default.Add, null) }, enabled = dens.size < Registro.MAX_DENSIDADES) }
                }
            }
            if (dens.isNotEmpty()) item { DensidadCard(vm, r, n, textos, activo, { activo = it }) { borrar = n } }
            item { Resumen(r) { vm.seleccion.value = it } }
            item {
                Bloque("Observaciones y firmas") {
                    OutlinedTextField(r.observaciones, { v -> vm.editar { it.copy(observaciones = v) } }, label = { Text("Observaciones") }, minLines = 3, modifier = Modifier.fillMaxWidth().visibleAlEnfocar { activo = null })
                    Par({ Campo("Elaboró", r.elaboro) { v -> vm.editar { it.copy(elaboro = v) } } }, { Campo("Aprobó", r.aprobo) { v -> vm.editar { it.copy(aprobo = v) } } })
                }
            }
        }
        }
        }
    }
    borrar?.let { i -> ConfirmDialog("Eliminar densidad ${i + 1}", "Se borrarán sus datos.", "Eliminar", true, onConfirm = { vm.eliminarDensidad(i) }, onDismiss = { borrar = null }) }
}

@Composable
private fun Bloque(titulo: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = Condor.NavyDeep)
            content()
        }
    }
}

/** Al enfocar un campo de texto se oculta el teclado numérico propio. */
private val LocalAlEnfocarTexto = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
private fun Campo(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth().visibleAlEnfocar(LocalAlEnfocarTexto.current))

@Composable
private fun Par(a: @Composable () -> Unit, b: @Composable () -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { a() }; Box(Modifier.weight(1f)) { b() } }

val NOMBRES = mapOf("a" to "Peso frasco y arena inicial", "b" to "Peso frasco y arena restante", "d" to "Constante del cono", "f" to "Densidad de la arena",
    "h" to "Material retenido en 3/4", "i" to "Gravedad específica bulk", "j" to "Peso material extraído húmedo", "l" to "Humedad (decimal)",
    "q" to "Densidad máxima de laboratorio (De)", "r" to "Humedad óptima (%)")

/** Explica en palabras simples por qué no se puede calcular S = P / Q × 100. */
private fun motivoS(r: LegacyResult): String {
    val falta = r.steps.firstOrNull { it.symbol == "S" }?.unavailableReason ?: return ""
    val errores = r.blocking.map { "${it.field}: ${it.message}" }
    return "No se calcula la compactación. " + (if (errores.isNotEmpty()) errores.joinToString("; ") else falta.replace("Depende de resultado no disponible", "Falta calcular"))
}

/** Fila calculada (fórmula del Excel): solo lectura, sombreada. */
@Composable
private fun Calculada(sym: String, nombre: String, valor: String, destacada: Boolean = false) {
    Row(Modifier.fillMaxWidth().background(if (destacada) Condor.Navy else Condor.Mist.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val c = if (destacada) Color.White else Condor.NavyDeep
        Text("$sym · $nombre", Modifier.weight(1f), color = c, style = MaterialTheme.typography.bodyMedium)
        Text(valor, color = c, fontWeight = FontWeight.SemiBold)
    }
}

private fun error(r: LegacyResult, sym: String) = r.issues.filter { it.field == sym }.joinToString("; ") { it.message }.ifBlank { null }
private fun v(r: LegacyResult, s: String, dec: Int, unidad: String) = r.value(s)?.let { "${Fmt.num(it, dec)} $unidad" } ?: "—"

@Composable
private fun DensidadCard(vm: RegistroViewModel, reg: Registro, n: Int, textos: Map<String, String>, activo: String?, activar: (String?) -> Unit, onBorrar: () -> Unit) {
    val d = reg.densidades[n]
    val r = reg.calcular(d)
    @Composable fun num(campo: String, unidad: String, ayuda: String? = null) {
        val sym = campo.uppercase()
        CampoNumero(sym, NOMBRES[campo]!!, unidad, vm.texto(n, campo), activo == "$n.$campo", { activar("$n.$campo") }, error(r, sym), ayuda)
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Densidad No. ${n + 1}", style = MaterialTheme.typography.titleMedium, color = Condor.NavyDeep, modifier = Modifier.weight(1f))
                IconButton(onClick = onBorrar) { Icon(Icons.Default.Delete, "Eliminar densidad") }
            }
            Par({ Campo("Capa No.", d.capa) { v -> vm.editarDensidad(n) { it.copy(capa = v) } } }, { Campo("Espesor capa", d.espesor) { v -> vm.editarDensidad(n) { it.copy(espesor = v) } } })
            Par({ Campo("PR", d.pr) { v -> vm.editarDensidad(n) { it.copy(pr = v) } } }, { Campo("Costado", d.costado) { v -> vm.editarDensidad(n) { it.copy(costado = v) } } })
            num("a", "g")
            num("b", "g")
            Calculada("C", "Peso arena total usada", v(r, "C", 0, "g"))
            num("d", "g")
            Calculada("E", "Peso arena en el hueco", v(r, "E", 0, "g"))
            num("f", "g/cm³")
            Calculada("G", "Volumen del hueco", v(r, "G", 2, "cm³"))
            num("h", "g")
            num("i", "g/cm³", "Mismo valor para todas las densidades")
            num("j", "g")
            Calculada("K", "% retenido 3/4", v(r, "K", 2, "%"))
            num("l", "", "Decimal, como en el Excel: 0,061")
            Calculada("M", "Densidad húmeda de campo", v(r, "M", 2, "kg/m³"))
            Calculada("N", "Humedad partículas finas (decimal)", r.value("N")?.let { Fmt.num(it, 4) } ?: "—")
            Calculada("O", "Densidad seca muestra total", v(r, "O", 2, "kg/m³"))
            Calculada("P", "Densidad seca corregida (Di)", v(r, "P", 2, "kg/m³"), destacada = true)
            num("q", "kg/m³", "Mismo valor para todas las densidades")
            num("r", "%", "En porcentaje: 6,68 · mismo valor para todas")
            Calculada("S", "Compactación del terreno", v(r, "S", 2, "%"), destacada = true)
            if (r.value("S") == null) Text(motivoS(r), color = Condor.Bad, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Resumen(r: Registro, seleccionar: (Int) -> Unit) {
    val cols = r.densidades.mapIndexed { i, d -> i to r.calcular(d) }
    Bloque("Resumen del registro") {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            listOf("No." to 0.6f, "PR" to 1.6f, "Di (kg/m³)" to 1.4f, "Comp. (%)" to 1.1f).forEach { (t, w) ->
                Text(t, Modifier.weight(w), style = MaterialTheme.typography.labelLarge, color = Condor.Steel)
            }
        }
        HorizontalDivider()
        cols.forEach { (i, res) ->
            Row(Modifier.fillMaxWidth().clickable { seleccionar(i) }.padding(vertical = 8.dp)) {
                Text("${i + 1}", Modifier.weight(0.6f)); Text(r.densidades[i].pr.ifBlank { "—" }, Modifier.weight(1.6f))
                Text(Fmt.num(res.value("P"), 2), Modifier.weight(1.4f)); Text(Fmt.num(res.value("S"), 2), Modifier.weight(1.1f), fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider()
        val q = DecimalText.storedToDouble(r.q)
        val colsPdf = cols.map { (i, res) -> PlanillaBuilder.Columna(i + 1, "", "", "", "", null, null, null, null, null, null, null, null, res) }
        val (kv, notas) = PlanillaBuilder.resumen(colsPdf, q)
        kv.forEach { (k, v) -> KeyValue(k, v, valueColor = if (k == "Resultado" && v.startsWith("NO")) Condor.Bad else if (k == "Resultado" && v.startsWith("CUMPLE")) Condor.Ok else MaterialTheme.colorScheme.onSurface) }
        notas.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Condor.Steel) }
    }
}
