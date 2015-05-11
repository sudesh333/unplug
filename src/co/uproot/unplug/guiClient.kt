package co.uproot.unplug.gui

import javafx.application.Application
import javafx.stage.Stage
import kotlinfx.builders.*
import kotlinfx.kalium.*
import kotlinfx.properties.*
import javafx.beans.property.SimpleStringProperty
import org.fxmisc.easybind.EasyBind
import javafx.scene.control.ListCell
import javafx.util.Callback
import javafx.scene.layout.Priority
import javafx.beans.value.ObservableNumberValue
import javafx.collections.FXCollections
import javafx.concurrent.Worker
import co.uproot.unplug.*
import javafx.beans.binding.DoubleBinding
import javafx.geometry.Pos
import java.util.concurrent.ConcurrentHashMap
import javafx.scene.image.Image
import sun.util.resources.cldr.rof.CalendarData_rof_TZ
import java.security.PrivilegedAction
import java.security.PrivilegedActionException
import java.util.function.BiFunction



fun main(args: Array<String>) {
  Application.launch(javaClass<UnplugApp>(), *args)
}

class UnplugApp : Application() {
  override fun start(stage: Stage?) {
    val args = getParameters().getRaw()
    val userIdInit = if (args.size() > 0) args[0] else ""
    val serverInit = if (args.size() > 1) args[1] else null
    val passwordInit = if (args.size() > 2) args[2] else ""

    val loginService = LoginService()
    val userId = TextField(userIdInit) { promptText = "Eg: bob" }

    // TODO: Use the auto-complete box from here: https://github.com/privatejava/javafx-autocomplete-field
    val serverCommonUrls = FXCollections.observableArrayList("https://matrix.org", "http://localhost:8008")
    val serverCombo = ComboBox(serverCommonUrls) {
      editable = true

      if (serverInit == null) {
        getSelectionModel().select(0)
      } else {
        getEditor().text = serverInit
      }
    }
    val password = PasswordField() {text = passwordInit}
    password.setOnAction {
      val serverText = serverCombo.editor.text()
      startLogin(loginService, serverText, stage!!)
    }

    val login = Button("Login")
    val loginStatus = Label("")

    loginStatus.textProperty().bind(loginService.messageProperty())
    loginService.userName.bind(userId.textProperty())
    loginService.password.bind(password.textProperty())

    val loginForm = GridPane(padding=Insets(left=10.0)) {
      hgap = 10.0
      vgap = 10.0
      addRow(0, Label("User id: "), userId)
      addRow(1, Label("Server: "), serverCombo)
      addRow(2, Label("Password: "), password)
      addRow(3, login)
      addRow(4, loginStatus)
    }

    login.setOnAction {
      val serverText = serverCombo.editor.text()
      startLogin(loginService, serverText, stage!!)
    }

    loginForm.disableProperty().bind(loginService.stateProperty().isNotEqualTo(Worker.State.READY))
    login.disable {
      userId.text().length() == 0 || serverCombo.editor.text().length() == 0
    }
    loginStatus.visible { loginStatus.text().length() > 0 }

    Stage(stage, title="unplug") {
      scene = Scene {
        stylesheets.add("/chat.css")
        root = VBox(spacing=10.0, padding=Insets(10.0)) {
          + loginForm
          + loginStatus
        }
      }
    }.show()
  }

  private fun startLogin(loginService: LoginService, serverText:String, stage: Stage) {
    // logginIn u true
    loginService.baseURL = "$serverText/"
    loginService.setOnSucceeded {
      val loginResult = loginService.getValue();
      if (loginResult == null) {
        // logginIn u false
        loginService.reset()
      } else {
        stage.title = "[unplug] " + loginService.userName.get() + " : " + serverText
        postLogin(stage, loginResult,serverText)
      }
    }
    loginService.setOnFailed {
      loginService.reset()
    }
    loginService.restart()
  }

  val status = SimpleStringProperty("Loading")

  val appState = AppState()

  fun postLogin(stage: Stage, loginResult: LoginResult,serverText:String) {
    val statusLabel = Label()
    statusLabel.textProperty().bind(status)
    statusLabel.visible { status.get().length() > 0 }

    val userListView = ListView(appState.currUserList.get()) {
      getStyleClass().add("user-list")
    }
    userListView.itemsProperty().bind(appState.currUserList)

    userListView.setCellFactory (object : Callback<javafx.scene.control.ListView<UserState>, ListCell<UserState>> {
      override fun call(list:javafx.scene.control.ListView<UserState>):ListCell<UserState> {
        return UserFormatCell()
      }
    })

    val createRoom=Button("Create Room")
    createRoom.setOnAction {
       val stage1=Stage()
      Stage(stage1, title = "Creating Room"){
        scene=Scene {
          stylesheets.add("/chat.css")
          val name = Label("Enter name :")
          val text = TextField()
          val visibility=RadioButton("public")
          val create = Button("Create")
          create.setOnAction {
            val createRoom1=CreateRoomService(loginResult,text.text(),visibility.getText())
            createRoom1.start()
            stage1.close()

          }
          val hb = HBox(spacing = 10.0) {
            +name
            +text
          }
          root = VBox(spacing = 15.0,padding =Insets(60.0,60.0,150.0,60.0)) {
            +visibility
            +hb
            +create
          }

        }
      }.show()

      //stage1.centerOnScreen()
      //stage1.setHeight(200.0)
      //stage1.setWidth(500.0)
    }

    val joinRoom=Button("Join Room")
    joinRoom.setOnAction {
      val stage2=Stage()
      Stage(stage2, title = "Joining Room"){
       scene=Scene{
         stylesheets.add("/chat.css")
         val lblName=Label("Enter room name ")
         val name=TextField()
         val join=Button("Join")
         join.setOnAction {
           try {
              val joinRoom=JoinRoomService(loginResult,name.text())
             joinRoom.start()
           }catch(e:Exception){
             e.printStackTrace()
             val stage11=Stage()
             Stage(stage11,title="Alert!!!"){
               scene=Scene{
                 stylesheets.add("/chat.css")
                 val msg=Label("Room is private")
                 msg.setPadding(Insets(20.0))
                 val close=Button("Close")
                 root=VBox(spacing = 15.0,padding = Insets(80.0,60.0,60.0,60.0)){
                   +msg
                   +close
                 }
                 close.setOnAction {
                   stage11.close()
                 }
               }

             }.show()
             //stage11.setWidth(500.0)
           }
           stage2.close()
         }

         val hbox=HBox(spacing=10.0){
           +lblName
           +name
       }
         root=VBox(spacing=10.0,padding = Insets(80.0,60.0,60.0,60.0)){
           +hbox
           +join
         }
       }

      }.show()
    }

    val banMember=Button("Ban Member")
    banMember.setOnAction {
      val stage3=Stage()
      Stage(stage3, title = "Banning Member"){
        scene=Scene{
          stylesheets.add("/chat.css")
          val lblroomName=Label("Enter room name")
          val roomName=TextField()
          val lblMId=Label("Enter member Id")
          val memId=TextField()
          val ban=Button("Ban")
          ban.setOnAction {
            val banService=BanRoomService(loginResult,roomName.text(),memId.text(),appState)
            banService.start()
            stage3.close()
          }

          val hbox1=HBox(spacing = 20.0){
            +lblroomName
            +roomName
          }
          val hbox2=HBox(spacing = 15.0){
            +lblMId
            +memId
          }
          root=VBox(spacing = 15.0,padding = Insets(80.0,60.0,60.0,60.0)){
            +hbox1
            +hbox2
            +ban
          }
        }
      }.show()
    }

    val leaveRoom=Button("Leave Room")
    leaveRoom.setOnAction {
      val stage4=Stage()
      Stage(stage4, title = "Leaving Room"){
        scene=Scene{
          stylesheets.add("/chat.css")
          val lblname=Label("Enter room name")
          val name=TextField()
          val leave=Button("Leave")
          leave.setOnAction {
            val leaveRoomSer = LeaveRoomService(loginResult, name.text())
            leaveRoomSer.start()
            stage4.close()
          }
            val hbox=HBox(spacing = 10.0){
              +lblname
              +name
            }
            root=VBox(spacing = 10.0, padding = Insets(80.0,60.0,60.0,60.0)){
              +hbox
              +leave
            }
        }
      }.show()
    }

    val roomListView = ListView(appState.roomNameList)

    val selectedRoomIndexProperty = roomListView.selectionModel.selectedIndexProperty()
    EasyBind.subscribe(selectedRoomIndexProperty, {indexNum ->
      val index = indexNum as Int
      if (index >= 0) {
        val room = appState.roomStateList.get(index)
        appState.currRoomId.set(room.id)
      } else {
        appState.currRoomId.set(null)
      }

    })

    val messageListView = ListView(appState.currChatMessageList.get()) {
      fixedCellSize = -1.0
    }

    messageListView.itemsProperty().bind(appState.currChatMessageList)

    messageListView.setCellFactory (object : Callback<javafx.scene.control.ListView<Message>, ListCell<Message>> {
      override fun call(list:javafx.scene.control.ListView<Message>):ListCell<Message> {
        return MessageFormatCell(messageListView.widthProperty().add(-20), appState)
      }
    })

    val messageInputView = TextField()
    messageInputView.setOnAction {
      val msg = messageInputView.text
      if(msg!="")
      {
        messageInputView.text = ""
        val sendService = SendMessageService(loginResult, appState.currRoomId.get(), msg)
        sendService.start()
      }
    }

    val optionView=VBox(spacing=10.0) {
      +createRoom
      +joinRoom
      +banMember
      +leaveRoom
    }

    val messageView = VBox(spacing = 10.0) {
      +messageListView
      +messageInputView
    }

    javafx.scene.layout.HBox.setHgrow(messageView, Priority.ALWAYS)
    javafx.scene.layout.HBox.setHgrow(messageInputView, Priority.ALWAYS)
    javafx.scene.layout.VBox.setVgrow(messageListView, Priority.ALWAYS)


    val chatView = HBox(spacing=10.0, padding=Insets(10.0)) {
      + optionView
      + roomListView
      + messageView
      + userListView
    }

    javafx.scene.layout.VBox.setVgrow(chatView, Priority.ALWAYS)

    stage.setScene(Scene {
      stylesheets.add("/chat.css")

      root = VBox(spacing=10.0, padding=Insets(10.0)) {
        + statusLabel
        + chatView
      }
    })

    stage.setMaximized(true)
    stage.centerOnScreen()

    val syncService = SyncService(loginResult)
    status.bind(syncService.messageProperty())
    syncService.setOnSucceeded {
      val syncResult = syncService.getValue()
      if (syncResult == null) {
        showSyncError()
      } else {
        appState.processSyncResult(syncResult, loginResult.api)
        postSync(loginResult)
      }
    }
    syncService.setOnFailed { showSyncError() }

    syncService.start()
  }

  private fun showSyncError() {
    Stage(null, "Error") {
      scene = Scene() {
        root = VBox() {
          +Label ("Error while syncing")
        }
      }
    }
  }

  fun postSync(loginResult: LoginResult) {
    val eventsService = EventService(loginResult)
    status.bind(eventsService.messageProperty())
    eventsService.setOnSucceeded {
      val eventResult = eventsService.getValue()
      if (eventResult != null) {
        appState.processEventsResult(eventResult, loginResult.api,loginResult)
      }
      eventsService.restart()
    }

    eventsService.setOnFailed {
      eventsService.restart()
    }

    eventsService.start()
  }
}

object ImageCache {

  final private class ImageEntry(val url:String, val img: Image)

  private val imageStore = ConcurrentHashMap<String, ImageEntry>()

  private class ImageMakerAndUpdater(val url: String) : BiFunction<String, ImageEntry, ImageEntry> {
    override fun apply(id: String, oldImg: ImageEntry?): ImageEntry {
      val saneURL = if (url.isEmpty()) "/default-avatar-32.png" else url
      val imgEntry = if (oldImg == null) {
        ImageEntry(saneURL,  Image(saneURL, 32.0, 32.0, true, true, true))
      } else {
        if (oldImg.url == saneURL) {
          oldImg
        } else {
          ImageEntry(saneURL,  Image(saneURL, 32.0, 32.0, true, true, true))
        }
      }
      return imgEntry
    }
  }

  fun getOrCreate(userId:String, url:String):Image {
    return imageStore.compute(userId, ImageMakerAndUpdater(url)).img
  }
}

class UserFormatCell() : ListCell<UserState>() {

  override fun updateItem(us: UserState?, empty: Boolean) {
    super.updateItem(us, empty)
    if (us == null || empty) {
      setGraphic(null)
    } else {
      val typing = us.typing.get()
      val present = us.present.get()
      val typingStr = if(typing) "⌨" else ""
      val id = Text("${us.id} $typingStr") {
        getStyleClass().add("unplug-text")
        getStyleClass().add("user-id")
      }
      val displayName = Text("${us.displayName.get()}") {
        getStyleClass().add("unplug-text")
        getStyleClass().add("user-displayname")
      }
      val userDetails =  VBox(spacing=2.0, padding=Insets(0.0)) {
        +id
        +displayName
      }

      val image = ImageCache.getOrCreate(us.id, us.avatarURL.get())
      val avatar = ImageView(image) {
        setCache(true)
        setPreserveRatio(true)
      }

      // The wrap ensures that the avatar image doesn't collapse when its width is smaller than 32
      val avatarWrap = StackPane() {
        +avatar

        setMinWidth(32.0)
        setAlignment(Pos.CENTER)
      }

      val graphic =  HBox(spacing=10.0, padding=Insets(2.0)) {
          +avatarWrap
          +userDetails

        setAlignment(Pos.CENTER_LEFT)

        if (typing || present) {
          if (typing) {
            getStyleClass().add("user-typing")
          }
          if (present) {
            getStyleClass().add("user-present")
          }
        } else {
          getStyleClass().add("user-default")
        }
      }
      setGraphic(graphic)
    }

  }
}


class MessageFormatCell(val containerWidthProperty: DoubleBinding, val appState: AppState) : ListCell<Message>() {
  override fun updateItem(message: Message?, empty: Boolean) {
    super.updateItem(message, empty)
    if (message == null || empty) {
      setGraphic(null)
    } else {
      val d = java.util.Date(message.ts)
      val (userId, time, msgBody) =
        when (message.type) {
          "m.room.create" -> Triple(message.content.getString("creator", "(unexpected missing creator)"), d, "Room created")
          "m.room.member" -> {
            val status = if (message.content.getString("membership", "") == "join") "Joined" else "Left"
            Triple(message.userId, d, status)
          }
          "m.room.message" -> Triple(message.userId, d, message.content.getString("body", "(unexpected empty body)"))
          else -> {
            Triple("Unknown message type: ${message.type}", "", "")
          }
        }

      if (message.roomId != null) {
        val users = appState.getRoomUsers(message.roomId)
        val messageUser = users.firstOrNull { it.id == message.userId }
        if (messageUser == null) {
          setGraphic(Text("TODO 1"))
        } else {
          if (messageUser.id == message.userId) {
            val url = messageUser.avatarURL
            val image = ImageCache.getOrCreate(message.userId, url.get())
            val avatar = ImageView(image) {
              setCache(true)
              setPreserveRatio(true)
            }

            // The wrap ensures that the avatar image doesn't collapse when its width is smaller than 32
            val avatarWrap = StackPane() {
              +avatar

              setMinWidth(32.0)
              setAlignment(Pos.CENTER)
            }

            val id = Text(userId)
            val time = Text(time.toString())
            val userDetails = VBox(spacing = 2.0, padding = Insets(0.0)) {
              +id
              +time
            }

            val body = Text(msgBody)
            val bodyFlow = TextFlow(body)

            val graphic = HBox(spacing = 10.0, padding = Insets(2.0)) {
              +avatarWrap
              +userDetails
              +bodyFlow
            }

            graphic.prefWidthProperty().bind(containerWidthProperty)

            id.getStyleClass().add("chat-message-text")
            time.getStyleClass().add("chat-message-text")
            body.getStyleClass().add("chat-message-text")

            setGraphic(graphic)
          } else {
            setGraphic(Text("TODO 2"))
          }

        }
      } else {
        throw IllegalStateException("Room id of message is null: " + message)
      }
    }
  }
}
