package com.unciv

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.*
import com.unciv.models.gamebasics.Unit
import com.unciv.models.stats.INamed
import com.unciv.ui.GameSettings
import com.unciv.ui.NewGameScreen
import com.unciv.ui.utils.getRandom
import com.unciv.ui.worldscreen.WorldScreen

class UnCivGame : Game() {
    var gameInfo: GameInfo = GameInfo()
    var settings = GameSettings()

    var worldScreen: WorldScreen? = null

    override fun create() {
        setupGameBasics()

        Current = this
        if (GameSaver().getSave("Autosave").exists()) {
            try {
                loadGame("Autosave")
            } catch (ex: Exception) { // silent fail if we can't read the autosave
                startNewGame()
            }
        }
        else startNewGame()
    }

    fun loadGame(gameName:String){
        gameInfo = GameSaver().loadGame( gameName)
        worldScreen = WorldScreen()
        setWorldScreen()
    }

    fun startNewGame(saveTutorialState:Boolean = false) {
        if(saveTutorialState) {
            val tutorials = gameInfo.tutorial
            gameInfo = GameInfo()
            gameInfo.tutorial = tutorials
        }
        else gameInfo = GameInfo()

        gameInfo.tileMap = TileMap(20)
        gameInfo.civilizations.add(CivilizationInfo("Babylon", Vector2.Zero, gameInfo)) // first one is player civ

        val freeTiles = gameInfo.tileMap.values.toMutableList()
        freeTiles.removeAll(gameInfo.tileMap.getTilesInDistance(Vector2.Zero,6))

        val barbarianCivilization = CivilizationInfo()
        gameInfo.civilizations.add(barbarianCivilization)// second is barbarian civ

        for (civname in listOf("Greece","China","Egypt")) {
            val startingLocation = freeTiles.toList().getRandom().position
            gameInfo.civilizations.add(CivilizationInfo(civname, startingLocation, gameInfo)) // all the rest whatever
            freeTiles.removeAll(gameInfo.tileMap.getTilesInDistance(startingLocation, 6))
        }

        barbarianCivilization.civName = "Barbarians"

        gameInfo.setTransients() // needs to be before placeBarbarianUnit because it depends on the tilemap having its gameinfo set

        (1..5).forEach { gameInfo.placeBarbarianUnit(freeTiles.toList().getRandom()) }


        worldScreen = WorldScreen()
        setWorldScreen()

    }

    fun setWorldScreen() {
        setScreen(worldScreen)
        worldScreen!!.update()
        Gdx.input.inputProcessor = worldScreen!!.stage
    }

    private fun <T> getFromJson(tClass: Class<T>, name: String): T {
        val jsonText = Gdx.files.internal("jsons/$name.json").readString()
        return Json().fromJson(tClass, jsonText)
    }

    private fun <T : INamed> createHashmap(items: Array<T>): LinkedHashMap<String, T> {
        val hashMap = LinkedHashMap<String, T>()
        for (item in items)
            hashMap[item.name] = item
        return hashMap
    }

    private fun setupGameBasics() {
        GameBasics.Buildings += createHashmap(getFromJson(Array<Building>::class.java, "Buildings"))
        GameBasics.Terrains += createHashmap(getFromJson(Array<Terrain>::class.java, "Terrains"))
        GameBasics.TileResources += createHashmap(getFromJson(Array<TileResource>::class.java, "TileResources"))
        GameBasics.TileImprovements += createHashmap(getFromJson(Array<TileImprovement>::class.java, "TileImprovements"))
        GameBasics.Helps += createHashmap(getFromJson(Array<BasicHelp>::class.java, "BasicHelp"))
        GameBasics.Units += createHashmap(getFromJson(Array<Unit>::class.java, "Units"))
        GameBasics.PolicyBranches += createHashmap(getFromJson(Array<PolicyBranch>::class.java, "Policies"))
        GameBasics.Civilizations += createHashmap(getFromJson(Array<Civilization>::class.java, "Civilizations"))

        // ...Yes. Total Voodoo. I wish I didn't have to do this.
        val x = LinkedHashMap<String,com.badlogic.gdx.utils.Array<com.badlogic.gdx.utils.Array<String>>>()
        val tutorials = getFromJson(x.javaClass, "Tutorials")
        for (tut in tutorials)
            GameBasics.Tutorials[tut.key] = tut.value.map{it.joinToString("\r\n")}

        val techColumns = getFromJson(Array<TechColumn>::class.java, "Techs")
        for (techColumn in techColumns) {
            for (tech in techColumn.techs) {
                tech.cost = techColumn.techCost
                tech.column = techColumn
                GameBasics.Technologies[tech.name] = tech
            }
        }
        for (building in GameBasics.Buildings.values) {
            if (building.requiredTech == null) continue
            val column = building.getRequiredTech().column
            if (building.cost == 0)
                building.cost = if (building.isWonder) column!!.wonderCost else column!!.buildingCost
        }

        for (branch in GameBasics.PolicyBranches.values) {
            branch.requires = ArrayList()
            branch.branch = branch.name
            for (policy in branch.policies) {
                policy.branch = branch.name
                if (policy.requires == null) policy.requires = arrayListOf(branch.name)
            }
            branch.policies.last().name = branch.name + " Complete"
        }
    }

    companion object {
        lateinit var Current: UnCivGame
    }

}

