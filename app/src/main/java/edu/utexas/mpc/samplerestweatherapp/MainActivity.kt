package edu.utexas.mpc.samplerestweatherapp

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.support.constraint.ConstraintLayout
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.LegendRenderer
import com.jjoe64.graphview.series.BarGraphSeries
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.w3c.dom.Text
import java.util.*

var weatherDescrip = ""
var weatherTemp = 0.0
var maxTemp = 0.0
var minTemp = 0.0
var humidity = 0
var maxTempTmw = 0.0
var minTempTmw = 0.0
var humidityTmw = 0
var seven = ""
var dailysteps = 0
var dailygoal = 0

class MainActivity : AppCompatActivity() {

    // I'm using lateinit for these widgets because I read that repeated calls to findViewById
    // are energy intensive
    lateinit var textView: TextView
    lateinit var stepgoalView: TextView
    lateinit var stepsView: TextView
    lateinit var steptmwView: TextView
    lateinit var resultsView: TextView
    lateinit var allButton: Button
    lateinit var streakView: TextView
    lateinit var homeButton: Button
    lateinit var graphButton: Button
    lateinit var goalsButton: Button
    lateinit var pageHome: ConstraintLayout
    lateinit var pageGraph: ConstraintLayout
    lateinit var graph: GraphView
    lateinit var holdseven: TextView

    lateinit var pageGoals: ConstraintLayout
    lateinit var goalDescView: TextView
    lateinit var goalDuraEdit: EditText
    lateinit var goalStepEdit: EditText
    lateinit var goalStepOutEdit: EditText
    lateinit var goalSetButton: Button
    lateinit var goalStatusView: TextView
    lateinit var goalRewardView: TextView

    lateinit var queue: RequestQueue
    lateinit var gson: Gson
    lateinit var futureWeatherResult: Forecast
    lateinit var mostRecentWeatherResult: WeatherResult

    // I'm doing a late init here because I need this to be an instance variable but I don't
    // have all the info I need to initialize it yet
    lateinit var mqttAndroidClient: MqttAndroidClient
    
    // you may need to change this depending on where your MQTT broker is running
    val serverUri = "tcp://192.168.4.8"//"tcp://10.147.160.62"
    // you can use whatever name you want to here
    val clientId = "EmergingTechMQTTClient"

    //these should "match" the topics on the "other side" (i.e, on the Raspberry Pi)
    val subscribeTopic = "steps"
    val subscribeTopicm = "msteps"
    val subscribeTopicr = "results"
    val subscribeTopics = "streak"
    val subscribeTopic7 = "seven"
    val subscribeTopicg = "usergoalr"
    val publishTopic = "weather"
    val publishTopicg = "usergoal"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = this.findViewById(R.id.text)
        stepsView = this.findViewById(R.id.steps)
        stepgoalView = this.findViewById(R.id.goal)
        steptmwView = this.findViewById(R.id.goaltmw)
        resultsView = this.findViewById(R.id.results)
        streakView = this.findViewById(R.id.streak)

        allButton = this.findViewById(R.id.all)

        homeButton = this.findViewById(R.id.thome)
        graphButton = this.findViewById(R.id.tgraph)
        goalsButton = this.findViewById(R.id.tgoals)
        pageHome = this.findViewById(R.id.pageHome)
        pageGraph = this.findViewById(R.id.pageGraph)
        pageGoals = this.findViewById(R.id.pageGoals)
        graph = this.findViewById(R.id.graph)
        holdseven = this.findViewById(R.id.holdseven)

        goalDescView = this.findViewById(R.id.goalDescrip)
        goalDuraEdit = this.findViewById(R.id.duration)
        goalStepEdit = this.findViewById(R.id.goalstep)
        goalStepOutEdit = this.findViewById(R.id.stepsoutput)
        goalSetButton = this.findViewById(R.id.setgoal)
        goalStatusView = this.findViewById(R.id.goalstatus)
        goalRewardView = this.findViewById(R.id.reward)

        graph.viewport.isXAxisBoundsManual = true
        graph.viewport.isYAxisBoundsManual = true
        graph.viewport.setMinX(-1.0)
        graph.viewport.setMaxX(7.0)
        graph.viewport.setMinY(0.0)
        graph.viewport.setMaxY(70.0)
        //graph.viewport.setScalableY(true)
        //graph.viewport.isScalable = true
        graph.viewport.setScrollableY(true)
        graph.viewport.isScrollable = true

        holdseven.setText("7 Days Steps Achievement")

        goalsButton.setOnClickListener({
            pageHome.visibility = View.GONE
            pageGraph.visibility = View.GONE
            pageGoals.visibility = View.VISIBLE
        })

        goalSetButton.setOnClickListener({sendUserGoal()})
        allButton.setOnClickListener({allTheWorkIsOnMe()})
        goalStepEdit.addTextChangedListener(object: TextWatcher
        {
            override fun afterTextChanged(p0: Editable?) {

            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                goalStepOutEdit.setText("${(p0.toString().toInt() + dailysteps)}/$dailygoal")
            }
        })

        graphButton.setOnClickListener({
            pageHome.visibility = View.GONE
            pageGraph.visibility = View.VISIBLE
            pageGoals.visibility = View.GONE
        })

        homeButton.setOnClickListener({
            pageHome.visibility = View.VISIBLE
            pageGraph.visibility = View.GONE
            pageGoals.visibility = View.GONE
        })

        queue = Volley.newRequestQueue(this)
        gson = Gson()

        /*syncButton = this.findViewById(R.id.MQTT)
        // when the user presses the syncbutton, this method will get called
        syncButton.setOnClickListener({ connectBroker() })*/

        // initialize the paho mqtt client with the uri and client id
        mqttAndroidClient = MqttAndroidClient(getApplicationContext(), serverUri, clientId);

        // when things happen in the mqtt client, these callbacks will be called
        mqttAndroidClient.setCallback(object: MqttCallbackExtended {

            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("Connection Complete!!")
                //syncButton.setText("Connected to Broker")
                // this subscribes the client to the subscribe topic
                mqttAndroidClient.subscribe(subscribeTopic, 0)
                mqttAndroidClient.subscribe(subscribeTopicm, 0)
                mqttAndroidClient.subscribe(subscribeTopicr, 0)
                mqttAndroidClient.subscribe(subscribeTopics, 0)
                mqttAndroidClient.subscribe(subscribeTopic7, 0)
                mqttAndroidClient.subscribe(subscribeTopicg, 0)

                Handler(Looper.getMainLooper()).postDelayed({
                    sendWeather()
                }, 1500)
            }

            // this method is called when a message is received that fulfills a subscription
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                println(message)
                if (topic == "usergoalr"){
                    if (message.toString() == "Y"){
                        goalStatusView.setText("You achieved you goal!")
                        goalRewardView.setText("My Reward Points: 100")
                    } else {
                        goalStatusView.setText("You did not achieve your goal, try again!")
                    }
                }
                if (topic == "seven"){
                    seven = message.toString()
                    val split = seven.split("^")
                    val bao = arrayOf(
                            DataPoint(0.0, 0.0),
                            DataPoint(1.0, 0.0),
                            DataPoint(2.0, 0.0),
                            DataPoint(3.0, 0.0),
                            DataPoint(4.0, 0.0),
                            DataPoint(5.0, 0.0),
                            DataPoint(6.0, 0.0)
                    )
                    var i = 0
                    for (line in split) {
                        val items = line.split(",")
                        bao[i] = DataPoint(i.toDouble(), items[1].toDouble())
                        println(items[1].toDouble())
                        i+=1
                    }
                    val series: BarGraphSeries<DataPoint> = BarGraphSeries<DataPoint>(bao)
                    graph.removeAllSeries()
                    graph.addSeries(series)
                    series.setSpacing(50);
                    series.setDrawValuesOnTop(true)
                    series.setValuesOnTopColor(Color.MAGENTA)
                }
                if (topic == "streak") {
                    streakView.setText("Streak Day: " + message.toString().split(",")[0])
                    if (message.toString().split(",")[1] == "Y"){
                        showRecipe()
                    }
                }
                if (topic == "msteps") {
                    stepsView.setText("Step Count: " + message.toString())
                    dailysteps = message.toString().toInt()
                }
                if (topic == "steps"){
                    stepgoalView.setText("Step Goal Today: " + message.toString().split(";")[0])
                    steptmwView.setText("Step Goal Tomorrow: " + message.toString().split(";")[1])
                    dailygoal = message.toString().split(";")[0].toInt()
                }
                if (topic == "results"){
                    if (message.toString().get(0).toString() == "G"){
                        resultsView.setTextColor(Color.parseColor("#4CAF50"))
                    }
                    else {
                        resultsView.setTextColor(Color.parseColor("#FF5722"))
                    }
                    resultsView.setText(message.toString())
                }
            }

            override fun connectionLost(cause: Throwable?) {
                println("Connection Lost")
            }

            // this method is called when the client succcessfully publishes to the broker
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("Delivery Complete")
            }

    })}

    fun sendUserGoal() {
        var userDura = goalDuraEdit.text.toString()
        var userStep = goalStepEdit.text.toString()
        //var custGoal = userDura + "," + userStep
        goalStatusView.setText("Timer started!")
        val sendMessage = MqttMessage()
        println("created message")
        sendMessage.payload = (userDura + "," + userStep).toByteArray()
        mqttAndroidClient.publish(publishTopicg, sendMessage)
        println(publishTopicg)
        println("published customize goal")
    }

    fun allTheWorkIsOnMe() {
        requestWeather()
        Handler(Looper.getMainLooper()).postDelayed({
            showDialog()
        }, 1000)
    }

    fun showRecipe() {
        val builder: AlertDialog.Builder? = this.let {
            AlertDialog.Builder(it)
        }
        builder?.setMessage("Congratulations on meeting your streak! Here is your gift recipe! https://www.allrecipes.com/recipe/20144/banana-banana-bread")?.setTitle("Your streak gift is here!")
        builder?.apply {
            /*setPositiveButton("OK",
                    DialogInterface.OnClickListener { dialog, id ->
                        startActivityForResult(Intent(Settings.ACTION_WIFI_SETTINGS), 88);
                    })
            setNegativeButton("NO",
                    DialogInterface.OnClickListener { dialog, id ->
                        Toast.makeText(this.context, "You need to connect to Raspberry Pi network to continue", Toast.LENGTH_LONG).show()
                    })*/
        }
        builder?.create()?.show()
    }

    fun showDialog() {
        val builder: AlertDialog.Builder? = this.let {
            AlertDialog.Builder(it)
        }
        builder?.setMessage("Please switch to the Raspberry Pi WiFi Network. Then press back to go back to app.")?.setTitle("Switch WiFi Networks")
        builder?.apply {
            setPositiveButton("OK",
                    DialogInterface.OnClickListener { dialog, id ->
                        startActivityForResult(Intent(Settings.ACTION_WIFI_SETTINGS), 88);
                    })
            setNegativeButton("NO",
                    DialogInterface.OnClickListener { dialog, id ->
                        Toast.makeText(this.context, "You need to connect to Raspberry Pi network to continue", Toast.LENGTH_LONG).show()
                    })
        }
        builder?.create()?.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 88) {
            Handler(Looper.getMainLooper()).postDelayed({
                connectBroker()
            }, 1500)
            Handler(Looper.getMainLooper()).postDelayed({
                println("before send")
                try {
                    sendWeather()
                    println("sent")
                }
                catch(e: Exception) {
                    println("catch")
                }
            }, 1500)
        }
    }

    fun requestWeather(){
        //sendButton.setText("Check Goal Meet")
        val url = StringBuilder("https://api.openweathermap.org/data/2.5/weather?q=austin,tx,us&appid=f43c0ae127ec1950ac62bec59a2402f8").toString()
        try {
            val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
                    com.android.volley.Response.Listener<String> { response ->
                        mostRecentWeatherResult = gson.fromJson(response, WeatherResult::class.java)
                        var url_compose = "http://openweathermap.org/img/wn/" + mostRecentWeatherResult.weather.get(0).icon + "@2x.png"
                        weatherDescrip = mostRecentWeatherResult.weather.get(0).description.capitalize()
                        weatherTemp = mostRecentWeatherResult.main.temp
                        maxTemp = mostRecentWeatherResult.main.temp_max
                        minTemp = mostRecentWeatherResult.main.temp_min
                        humidity = mostRecentWeatherResult.main.humidity
                        textView.text = weatherDescrip + " today"
                        val imageView: ImageView = findViewById(R.id.image_view)
                        Picasso.with(this).load(url_compose).into(imageView)
                    },
                    com.android.volley.Response.ErrorListener { println("******Request Weather didn't work!") }) {}
            // Add the request to the RequestQueue.
            queue.add(stringRequest)
        }
        catch (e: Exception) {
            Toast.makeText(this.applicationContext, "Please connect to WiFi", Toast.LENGTH_LONG).show()
            return
        }

        val futureurl = StringBuilder("https://api.openweathermap.org/data/2.5/forecast?q=austin,tx,us&cnt=16&appid=f43c0ae127ec1950ac62bec59a2402f8").toString()
        val stringRequestfuture = object : StringRequest(com.android.volley.Request.Method.GET, futureurl,
                com.android.volley.Response.Listener<String> { response ->
                    //textView.text = response
                    val currentTime: Date = Calendar.getInstance().time
                    var nextdayString = ""
                    println("here")
                    println(currentTime.date)
                    if (currentTime.date < 10){
                        nextdayString = (currentTime.year+1900).toString() + "-" + (currentTime.month+1) + "-0" + (currentTime.date+1)
                    } else {
                        nextdayString = (currentTime.year+1900).toString() + "-" + (currentTime.month+1) + "-" + (currentTime.date+1)
                    }
                    println("tomorrow date")
                    println(nextdayString)

                    futureWeatherResult = gson.fromJson(response, Forecast::class.java)
                    //mostRecentWeatherResult = gson.fromJson(response, WeatherResult::class.java)
                    for (fw in futureWeatherResult.list){
                        if (fw.dt_txt.indexOf(nextdayString) > -1 && fw.dt_txt.indexOf("12:00:00") > -1)
                        {
                            maxTempTmw = fw.main.temp_max
                            minTempTmw = fw.main.temp_min
                            humidityTmw = fw.main.humidity
                            println("received for tomorrow")
                            println(maxTempTmw.toString() + minTempTmw.toString() + humidityTmw.toString())
                        }
                    }
                },
                com.android.volley.Response.ErrorListener { println("******Request Weather didn't work!") }) {}
        // Add the request to the RequestQueue.
        queue.add(stringRequestfuture)
    }

    fun sendWeather(){
        //sendButton.setText("Checked Goal Meet")
        val sendMessage = MqttMessage()
        println("created message")
        sendMessage.payload = (weatherDescrip + ";" + weatherTemp.toString() + ";" + maxTemp.toString() + ";" + minTemp.toString() + ";" + humidity.toString() + ";" + maxTempTmw.toString() + ";" + minTempTmw.toString() + ";" + humidityTmw.toString()).toByteArray()
        println(weatherDescrip)
        println(weatherTemp)
        println(maxTemp)
        println(minTemp)
        println(humidity)
        // this publishes a message to the publish topic
        mqttAndroidClient.publish(publishTopic, sendMessage)
        println(publishTopic)
        println("published")
    }

    // this method just connects the paho mqtt client to the broker
    fun connectBroker(){
        println("+++++++ Connecting...")
        mqttAndroidClient.connect()
    }

}


class Forecast(val cod: String, val message: Int, val cnt: Int, val list:Array<WeatherTmw>)
class WeatherTmw(val dt_txt: String, val main: WeatherMain)
class WeatherResult(val id: Int, val name: String, val cod: Int, val coord: Coordinates, val main: WeatherMain, val weather: Array<Weather>)
class Coordinates(val lon: Double, val lat: Double)
class Weather(val id: Int, val main: String, val description: String, val icon: String)
class WeatherMain(val temp: Double, val pressure: Int, val humidity: Int, val temp_min: Double, val temp_max: Double)

