package io.github.fjossinet.rnartist.model

import io.github.fjossinet.rnartist.core.model.*


interface ExplorerItem {
    val name:String
    var location:String
    var color:String?
    var lineWidth:String?
    var lineShift:String?
    var opacity:String?
    var fullDetails:String?
    val drawingElement:DrawingElement?
    val residues:List<ResidueDrawing>

    fun applyTheme(theme: Theme)

    fun setDrawingConfigurationParameter(param: String, value: String?): String?

}

abstract class AbstractExplorerItem(name:String, drawingElement:DrawingElement? = null):ExplorerItem {

    override val name = name
    override var location = ""
    override var color:String? = null
    override var lineWidth:String? = null
    override var lineShift:String? = null
    override var opacity:String? = null
    override var fullDetails:String? = null
    override val drawingElement:DrawingElement? = drawingElement

}

class SecondaryStructureItem(val drawing:SecondaryStructureDrawing): AbstractExplorerItem("2D") {

    override var location = Location(1, drawing.length).toString()

    override var color:String? = null
        get () = null
    override var lineWidth:String? = null
        get () = null
    override var lineShift:String? = null
        get () = null
    override var opacity: String? = null
        get () = null
    override var fullDetails: String? = null
        get () = null

    override val residues:List<ResidueDrawing>
        get() =this.drawing.residues

    override fun setDrawingConfigurationParameter(param:String, value:String?): String? {
        if (value == null)
            this.drawing.drawingConfiguration.params[param] = RnartistConfig.defaultTheme.get(SecondaryStructureType.Full2D.toString())!![param.toString()]!! //we restore the default value
        else
            this.drawing.drawingConfiguration.params[param] = value
        return this.drawing.drawingConfiguration.params[param]
    }

    override fun applyTheme(theme: Theme) {
        theme.configurations.get(SecondaryStructureType.Full2D.toString())?.let { configuration ->
            configuration.keys.forEach {
                when(it) {
                    DrawingConfigurationParameter.Color.toString() -> this.color = configuration[it]
                    DrawingConfigurationParameter.LineWidth.toString() -> this.lineWidth = configuration[it]
                    DrawingConfigurationParameter.LineShift.toString() -> this.lineShift = configuration[it]
                    DrawingConfigurationParameter.Opacity.toString() -> this.opacity = configuration[it]
                    DrawingConfigurationParameter.FullDetails.toString() -> this.fullDetails = configuration[it]
                }
            }
        }
    }

}

abstract class StructuralItem(name:String, drawingElement:DrawingElement):AbstractExplorerItem(name, drawingElement) {

    override val name = name
    override var location = drawingElement.location.description

    override var color:String? = getHTMLColorString(drawingElement.drawingConfiguration.color)
        set(value) {
            field = this.setDrawingConfigurationParameter(DrawingConfigurationParameter.Color.toString(), value)
        }

    override var lineWidth:String? = drawingElement.drawingConfiguration.lineWidth.toString()
        set(value) {
            field = this.setDrawingConfigurationParameter(DrawingConfigurationParameter.LineWidth.toString(), value)
        }

    override var fullDetails:String? = drawingElement.drawingConfiguration.fullDetails.toString()
        set(value) {
            field = this.setDrawingConfigurationParameter(DrawingConfigurationParameter.FullDetails.toString(), value)
        }

    override var lineShift:String? = drawingElement.drawingConfiguration.lineShift.toString()
        set(value) {
            field = this.setDrawingConfigurationParameter(DrawingConfigurationParameter.LineShift.toString(), value)
        }

    override var opacity: String? = drawingElement.drawingConfiguration.opacity.toString()

        set(value) {
            field = this.setDrawingConfigurationParameter(DrawingConfigurationParameter.Opacity.toString(), value)
        }

    override val residues:List<ResidueDrawing>
        get() =this.drawingElement!!.residues

    override fun setDrawingConfigurationParameter(param:String, value:String?): String? {
        if (value == null)
            this.drawingElement!!.drawingConfiguration.params.remove(param)
        else
            this.drawingElement!!.drawingConfiguration.params[param] = value
        return this.drawingElement!!.drawingConfiguration.params[param]
    }

    override fun applyTheme(theme: Theme) {
        theme.configurations.get(this.drawingElement!!.type.toString())?.let { configuration ->
            configuration.keys.forEach {
                when(it) {
                    DrawingConfigurationParameter.Color.toString() -> this.color = configuration[it]
                    DrawingConfigurationParameter.LineWidth.toString() -> this.lineWidth = configuration[it]
                    DrawingConfigurationParameter.LineShift.toString() -> this.lineShift = configuration[it]
                    DrawingConfigurationParameter.Opacity.toString() -> this.opacity = configuration[it]
                    DrawingConfigurationParameter.FullDetails.toString() -> this.fullDetails = configuration[it]
                }
            }
        }
    }
}
class GroupOfStructuralElements(name:String) : AbstractExplorerItem(name) {

    val children = mutableListOf<ExplorerItem>()

    override var color:String?
        set(value) {
            children.forEach { it.color = value }
        }
        get () = ""
    override var lineWidth:String?
        set(value) {
            children.forEach { it.lineWidth = value }
        }
        get () = ""
    override var lineShift:String?
        set(value) {
            children.forEach { it.lineShift = value }
        }
        get () = ""
    override var opacity: String?
        set(value) {
            children.forEach { it.opacity = value }
        }
        get () = ""
    override var fullDetails: String?
        set(value) {
            children.forEach { it.fullDetails = value }
        }
        get () = ""

    override val residues:List<ResidueDrawing>
        get() = this.children.flatMap { it.residues }

    override fun setDrawingConfigurationParameter(param:String, value:String?) : String? {
        children.forEach { it.setDrawingConfigurationParameter(param, value) }
        return null
    }

    override fun applyTheme(theme: Theme) {
        children.forEach { it.applyTheme(theme) }
    }
}

class SingleStrandItem(val ss:SingleStrandDrawing): StructuralItem("${ss.name} [${ss.location}]", ss) {
    override var lineShift: String? = null
        get() = null

}

class JunctionItem(val junction:JunctionDrawing): StructuralItem("${junction.junctionCategory.name} ${junction.name} [${junction.location}]", junction) {
    override var lineShift: String? = null
        get() = null
}

class PknotItem(val pknot:PKnotDrawing): StructuralItem("${pknot.name} [${pknot.location}]", pknot) {
    override var lineShift: String? = null
        get() = null
}

class HelixItem(val helix:HelixDrawing): StructuralItem("${helix.name} [${helix.location}]", helix) {
    override var lineShift: String? = null
        get() = null
}

class TertiaryInteractionItem(val tertiaryInteraction:TertiaryInteractionDrawing): StructuralItem("${tertiaryInteraction.name} [${tertiaryInteraction.location}]", tertiaryInteraction) {

}

class SecondaryInteractionItem(val secondaryInteraction:SecondaryInteractionDrawing): StructuralItem("${secondaryInteraction.name} [${secondaryInteraction.location}]", secondaryInteraction) {

}

class PhosphodiesterItem(val phosphodiesterBondLine: PhosphodiesterBondDrawing): StructuralItem("${phosphodiesterBondLine.start}-${phosphodiesterBondLine.end}", phosphodiesterBondLine) {

}

class InteractionSymbolItem(val interactionSymbol:InteractionSymbolDrawing): StructuralItem("Symbol", interactionSymbol) {
    override var lineShift: String? = null
        get() = null
}

class ResidueItem(val residue:ResidueDrawing): StructuralItem("${residue.name}${residue.location.start}", residue) {

    override var lineShift: String? = null
        get() = null
}

class ResidueLetterItem(val residueLetter:ResidueLetterDrawing): StructuralItem("${residueLetter.name}", residueLetter) {

    override var lineShift: String? = null
        get() = null

    override var lineWidth: String? = null
        get() = null
}

class DefaultSymbolItem(val symbol:LWSymbolDrawing): StructuralItem("Default Symbol", symbol) {

    override var lineShift: String? = null
        get() = null

}

class LWSymbolItem(val symbol:LWSymbolDrawing): StructuralItem(symbol.toString(), symbol) {

    override var lineShift: String? = null
        get() = null

}