package com.ncs.o2.Domain.Repositories

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.ncs.o2.Constants.Errors
import com.ncs.o2.Constants.IDType
import com.ncs.o2.Constants.SwitchFunctions
import com.ncs.o2.Data.Room.TasksRepository.TasksDatabase
import com.ncs.o2.Domain.Interfaces.Repository
import com.ncs.o2.Domain.Interfaces.ServerErrorCallback
import com.ncs.o2.Domain.Models.CheckList
import com.ncs.o2.Domain.Models.CurrentUser
import com.ncs.o2.Domain.Models.Message
import com.ncs.o2.Domain.Models.Notification
import com.ncs.o2.Domain.Models.Segment
import com.ncs.o2.Domain.Models.ServerResult
import com.ncs.o2.Domain.Models.Tag
import com.ncs.o2.Domain.Models.Task
import com.ncs.o2.Domain.Models.TaskItem
import com.ncs.o2.Domain.Models.User
import com.ncs.o2.Domain.Models.UserInMessage
import com.ncs.o2.Domain.Models.UserInfo
import com.ncs.o2.Domain.Models.WorkspaceTaskItem
import com.ncs.o2.Domain.Utility.Codes
import com.ncs.o2.Domain.Utility.ExtensionsUtil.isNull
import com.ncs.o2.Domain.Utility.FirebaseUtils.awaitt
import com.ncs.o2.HelperClasses.PrefManager
import com.ncs.o2.UI.StartScreen.maintainceCheck
import com.ncs.versa.Constants.Endpoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.random.Random

/*
File : FirestoreRepository.kt -> com.ncs.o2.UI
Description : This is the class for firestore repository 

Author : Alok Ranjan (VC uname : apple)
Link : https://github.com/arpitmx
From : Bitpolarity x Noshbae (@Project : O2 Android)

Creation : 11:15 pm on 30/05/23

Todo >
Tasks CLEAN CODE : 
Tasks BUG FIXES : 
Tasks FEATURE MUST HAVE : 
Tasks FUTURE ADDITION : 

*/

//klinttest

@Suppress("UNCHECKED_CAST")
class FirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : Repository {
    private val firebaseFirestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storageReference = FirebaseStorage.getInstance().reference
    private val TAG: String = com.ncs.o2.Domain.Repositories.FirestoreRepository::class.java.simpleName
    lateinit var serverErrorCallback: ServerErrorCallback
//    private val editor : SharedPreferences.Editor by lazy {
//        pref.edit()
//    }
    @Inject
    lateinit var db:TasksDatabase

    fun getTaskPath(task: Task): String {
        return Endpoints.PROJECTS +
                "/${task.project_ID}" +
                "/${Endpoints.Project.SEGMENT}" +
                "/${task.segment}" +
                "/${Endpoints.Project.TASKS}" +
                "/${task.id}" +
                "/"

    }

    fun getNotificationsRef(toUser: String): CollectionReference {
        return firestore.collection(Endpoints.USERS).document(toUser)
            .collection(Endpoints.Notifications.NOTIFICATIONS)
        //Endpoints.USERS+"/${notification.fromUser}"+"/${Endpoints.Notifications.NOTIFICATIONS}"
    }

    fun getNotificationTimeStampPath(): String {
        return Endpoints.USERS +
                "/${FirebaseAuth.getInstance().currentUser!!.email}"

    }

    override suspend fun updateNotificationTimeStampPath(serverResult: (ServerResult<Long>) -> Unit) {

        val currentTimeStamp = HashMap<String, Any>()
        val currentTime = com.google.firebase.Timestamp.now().seconds
        currentTimeStamp[Endpoints.Notifications.NOTIFICATION_LAST_SEEN] = currentTime

        return try {
            serverResult(ServerResult.Progress)
            firestore.document(getNotificationTimeStampPath()).update(currentTimeStamp).await()
            serverResult(ServerResult.Success(currentTime))
        } catch (e: Exception) {
            serverResult(ServerResult.Failure(e))
        }
    }


    override suspend fun getNotificationLastSeenTimeStamp(serverResult: (ServerResult<Long>) -> Unit) {
        return try {
            serverResult(ServerResult.Progress)
            val lastSeenTimeStamp = firestore.document(getNotificationTimeStampPath()).get().await()
                .getLong(Endpoints.User.NOTIFICATION_TIME_STAMP)
            serverResult(ServerResult.Success(lastSeenTimeStamp!!))

        } catch (e: Exception) {
            serverResult(ServerResult.Failure(e))
        }
    }

    override fun maintenanceCheck(): LiveData<maintainceCheck> {
        val liveData = MutableLiveData<maintainceCheck>()

        firestore.collection("AppConfig")
            .document("maintenance")
            .get(Source.SERVER)
            .addOnSuccessListener { data ->
                if (data.exists()) {
                    val maintanenceChecks = data.data?.get("isMaintaining").toString()
                    val maintainceDesc = data.data?.get("Description").toString()

                    Codes.STRINGS.isMaintaining = maintanenceChecks
                    Codes.STRINGS.maintaninDesc = maintainceDesc


                    val checks = data.toObject(maintainceCheck::class.java)
                    liveData.postValue(checks!!)

                }
            }
            .addOnFailureListener {
                Timber.tag("checks").d("failed")
            }

        return liveData
    }

    override suspend fun getNewNotifications(
        lastSeenTimeStamp: Long,
        serverResult: (ServerResult<List<Notification>>) -> Unit
    ) {

        return try {
            serverResult(ServerResult.Progress)

            val notificationsCollection = firestore.collection(Endpoints.USERS)
                .document(FirebaseAuth.getInstance().currentUser!!.email!!)
                .collection(Endpoints.Notifications.NOTIFICATIONS)

            val query = notificationsCollection
                .orderBy(Endpoints.Notifications.TIMESTAMP, Query.Direction.DESCENDING)
                .whereGreaterThan(Endpoints.Notifications.TIMESTAMP, lastSeenTimeStamp)
                .get(Source.SERVER)
                .await()

            val newNotifications = CoroutineScope(Dispatchers.IO).async {
                query.documents.map { documentSnapshot ->


                    val notificationID: String =
                        documentSnapshot.getString(Endpoints.Notifications.notificationID)!!
                    val notificationType: String =
                        documentSnapshot.getString(Endpoints.Notifications.notificationType)!!
                    val taskID: String =
                        documentSnapshot.getString(Endpoints.Notifications.taskID)!!
                    val title: String = documentSnapshot.getString(Endpoints.Notifications.title)!!
                    val message: String =
                        documentSnapshot.getString(Endpoints.Notifications.message)!!
                    val fromUser: String =
                        documentSnapshot.getString(Endpoints.Notifications.fromUser)!!
                    val toUser: String =
                        documentSnapshot.getString(Endpoints.Notifications.toUser)!!
                    val timeStamp: Long =
                        documentSnapshot.getLong(Endpoints.Notifications.timeStamp)!!

                    Notification(
                        notificationID = notificationID,
                        notificationType = notificationType,
                        taskID = taskID,
                        title = title,
                        message = message,
                        fromUser = fromUser,
                        toUser = toUser,
                        timeStamp = timeStamp
                    )
                }
            }.await()

            serverResult(ServerResult.Success(newNotifications))

        } catch (e: Exception) {
            serverResult(ServerResult.Failure(e))
        }

    }

    override suspend fun postNotification(
        notification: Notification,
        serverResult: (ServerResult<Int>) -> Unit
    ) {

        return try {
            serverResult(ServerResult.Progress)
            getNotificationsRef(notification.toUser).add(notification).await()
            serverResult(ServerResult.Success(200))

        } catch (e: Exception) {
            serverResult(ServerResult.Failure(e))
        }

    }

    override fun uploadProjectIcon(
        bitmap: Bitmap,
        projectId: String
    ): LiveData<ServerResult<StorageReference>> {

        val liveData = MutableLiveData<ServerResult<StorageReference>>()
        val imageFileName =
            "${Endpoints.User.PROJECTS}/${projectId}${Endpoints.Storage.IMAGE_PATH}"
//            "${FirebaseAuth.getInstance().currentUser?.email}${Endpoints.Storage.DP_PATH}"
        val imageRef = storageReference.child(imageFileName)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, baos)
        val data = baos.toByteArray()
        val uploadTask = imageRef.putBytes(data)


        uploadTask.addOnSuccessListener {
            val userData = mapOf(
                "PHOTO_ADDED" to true,
            )

            firestore.collection("Users")
                .document(FirebaseAuth.getInstance().currentUser?.email!!)
                .update(userData)
                .addOnSuccessListener {
                    liveData.postValue(ServerResult.Success(imageRef))
                }
                .addOnFailureListener { e ->
                    liveData.postValue(ServerResult.Failure(e))
                }

        }.addOnFailureListener { exception ->
            liveData.postValue(ServerResult.Failure(exception))

        }

        return liveData
    }

    override fun getProjectIcon(reference: StorageReference): LiveData<ServerResult<StorageReference>> {
        TODO("Not yet implemented")
    }

    override fun getProjectIconUrl(reference: StorageReference): LiveData<ServerResult<String>> {

        val liveData = MutableLiveData<ServerResult<String>>()

        liveData.postValue(ServerResult.Progress)
        reference.downloadUrl
            .addOnSuccessListener { uri ->
                val imageUrl = uri.toString()
                liveData.postValue(ServerResult.Success(imageUrl))
            }
            .addOnFailureListener { exception ->
                liveData.postValue(ServerResult.Failure(exception))
            }
        return liveData
    }

    override fun addProjectImageUrlToFirestore(
        IconUrl: String,
        projectName: String
    ): LiveData<Boolean> {

        val liveData = MutableLiveData<Boolean>()
        firestore.collection("Projects")
            .document(projectName)
            .update("ICON_URL", IconUrl)
            .addOnSuccessListener {
                liveData.postValue(true)
            }
            .addOnFailureListener { exception ->
                // Handle failed Firestore update
                liveData.postValue(false)
            }
        return liveData
    }


    ////////////////////////////// FIREBASE USER DP FUNCTIONALITY //////////////////////////
    override fun uploadUserDP(bitmap: Bitmap): LiveData<ServerResult<StorageReference>> {

        val liveData = MutableLiveData<ServerResult<StorageReference>>()
        val imageFileName =
            "${FirebaseAuth.getInstance().currentUser?.email}${Endpoints.Storage.DP_PATH}"
        val imageRef = storageReference.child(imageFileName)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, baos)
        val data = baos.toByteArray()
        val uploadTask = imageRef.putBytes(data)


        uploadTask.addOnSuccessListener {
            val userData = mapOf(
                "PHOTO_ADDED" to true,
            )

            firestore.collection("Users")
                .document(FirebaseAuth.getInstance().currentUser?.email!!)
                .update(userData)
                .addOnSuccessListener {
                    liveData.postValue(ServerResult.Success(imageRef))
                }
                .addOnFailureListener { e ->
                    liveData.postValue(ServerResult.Failure(e))
                }

        }.addOnFailureListener { exception ->
            liveData.postValue(ServerResult.Failure(exception))

        }

        return liveData
    }

    override fun getUserDPUrl(reference: StorageReference): LiveData<ServerResult<String>> {

        val liveData = MutableLiveData<ServerResult<String>>()

        liveData.postValue(ServerResult.Progress)
        reference.downloadUrl
            .addOnSuccessListener { uri ->
                val imageUrl = uri.toString()
                liveData.postValue(ServerResult.Success(imageUrl))
            }
            .addOnFailureListener { exception ->
                liveData.postValue(ServerResult.Failure(exception))
            }
        return liveData
    }

    override fun addImageUrlToFirestore(DPUrl: String): LiveData<Boolean> {

        val liveData = MutableLiveData<Boolean>()
        firestore.collection("Users")
            .document(FirebaseAuth.getInstance().currentUser?.email!!)
            .update("DP_URL", DPUrl)
            .addOnSuccessListener {
                liveData.postValue(true)
            }
            .addOnFailureListener { exception ->
                // Handle failed Firestore update
                liveData.postValue(false)
            }
        return liveData
    }


    ////////////////////////////// FIREBASE USER DP FUNCTIONALITY //////////////////////////

    fun getProjectRef(projectID: String): DocumentReference {
        return firestore.collection(Endpoints.PROJECTS).document(projectID)
        // return Endpoints.PROJECTS + "/${projectID}"
    }

    fun generateRandomID(id: IDType): String {

        val random = Random(System.currentTimeMillis())
        val randomNumber = random.nextInt(10000, 99999)

        when (id) {
            IDType.UserID -> return "#U$randomNumber"
            IDType.TaskID -> return "#T$randomNumber"
            IDType.SegmentID -> return "#S$randomNumber"
        }
    }


    fun getProjectPath(projectID: String): String {
        return Endpoints.PROJECTS + "/${projectID}" + "/"
    }

    fun getTasksRepository(projectPath: String, isDuplicate: (List<String>) -> Unit) {
        firestore.document(projectPath)
            .get(Source.SERVER)
            .addOnSuccessListener { snap ->

                if (snap.exists()) {
                    val taskMap = snap.get(Endpoints.Project.TASKS) as Map<String, String>
                    val taskArrayList = taskMap.keys.toList()
                    isDuplicate(taskArrayList)

                } else {
                    Timber.tag(tag = TAG).d("No tasks exists")
                    isDuplicate(listOf())
                }
            }
            .addOnFailureListener {
                serverErrorCallback.handleServerException(it.message!!)
            }

    }


    fun uniqueIDfromList(idType: IDType, list: List<String>): String {
        var uniqueID: String

        when (idType) {
            IDType.UserID -> {
                do {
                    uniqueID = generateRandomID(idType)
                } while (list.contains(uniqueID))
            }

            IDType.TaskID -> {
                uniqueID = generateRandomID(idType)

            }

            IDType.SegmentID -> {
                uniqueID = generateRandomID(idType)

            }
        }

        return uniqueID
    }


    override suspend fun setFCMToken(token: String, serverResult: (ServerResult<Int>) -> Unit) {

        val userData = mapOf(
            Endpoints.User.FCM_TOKEN to token,
        )

        return try {

            serverResult(ServerResult.Progress)
            firestore.collection(Endpoints.USERS)
                .document(FirebaseAuth.getInstance().currentUser?.email!!)
                .update(userData).awaitt()
            serverResult(ServerResult.Success(200))

        } catch (e: Exception) {
            serverResult(ServerResult.Failure(e))
        }

    }

    override fun getTagbyId(
        id: String,
        projectName: String,
        result: (ServerResult<Tag>) -> Unit
    ) {

        firestore.collection(Endpoints.PROJECTS)
            .document(projectName)
            .collection(Endpoints.Project.TAGS)
            .whereEqualTo("tagID", id)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]

                    val tagText = document.getString("tagText")
                    val tagID = document.getString("tagID")
                    val textColor = document.getString("textColor")!!
                    val bgColor = document.getString("bgColor")
                    val projectName=document.getString("projectName")

                    val tag = Tag(
                        tagText = tagText!!,
                        tagID = tagID!!,
                        textColor = textColor,
                        bgColor = bgColor!!,
                        projectName = projectName!!
                    )
                    result(ServerResult.Success(tag))
                } else {
                    result(ServerResult.Failure(Exception("Document not found for title: $id")))
                }
            }
            .addOnFailureListener { exception ->
                result(ServerResult.Failure(exception))
            }
    }


    override fun createUniqueID(idType: IDType, projectID: String, generatedID: (String) -> Unit) {

        val projectPath = getProjectPath(projectID)
        when (idType) {
            IDType.TaskID -> {
                getTasksRepository(projectPath) { tasksArray ->
                    generatedID(uniqueIDfromList(idType, tasksArray))
                }
            }

            IDType.UserID -> {}
            IDType.SegmentID -> {}
        }

    }

    fun createRandomSegmentID(): String {
        val random = Random(System.currentTimeMillis())
        val randomNumber = random.nextInt(1000, 9999)
        return "#S$randomNumber"
    }

    override fun setCallback(callback: ServerErrorCallback) {
        this.serverErrorCallback = callback
    }

    private fun getSegmentRef(task: Task): DocumentReference {
        return firestore.collection(Endpoints.PROJECTS)
            .document(task.project_ID)

    }


    override suspend fun postTask(
        task: Task,
        checkList: MutableList<CheckList>,
        serverResult: (ServerResult<Int>) -> Unit
    ) {
        val appendTaskID = hashMapOf<String, Any>("TASKS.${task.id}" to "${task.segment}.TASKS")

        try {
            val workspaceTaskItem = WorkspaceTaskItem(id = task.id, status = "Assigned")

            firestore.runTransaction { transaction ->
                val segmentRef = getSegmentRef(task)
                val newLastUpdated = Timestamp.now()

                transaction.update(segmentRef, Endpoints.Project.LAST_UPDATED, newLastUpdated)
                PrefManager.putLastCacheUpdateTimestamp(newLastUpdated)
                val taskDocRef = segmentRef.collection(Endpoints.Project.TASKS).document(task.id)
                transaction.set(taskDocRef, task)
                for (i in 0 until checkList.size) {
                    val checklistDocRef =
                        taskDocRef.collection(Endpoints.Project.CHECKLIST).document(checkList[i].id)
                    transaction.set(checklistDocRef, checkList[i])
                }

            }.addOnSuccessListener {
                CoroutineScope(Dispatchers.IO).launch {
                    db.tasksDao().insert(task)
                }
                serverResult(ServerResult.Success(200))
            }.addOnFailureListener { exception ->
                serverResult(ServerResult.Failure(exception))
            }
        } catch (exception: Exception) {
            serverResult(ServerResult.Failure(exception))
        }
    }



    override suspend fun addTask(task: Task) {
        try {
            firestore.collection(Endpoints.PROJECTS).document(PrefManager.getcurrentProject())
                .collection(Endpoints.Project.TASKS).document(task.id).set(task)
                .await()

        } catch (e: Exception) {
            // Handle exceptions appropriately
            Log.e("addTaskChecking", "Error adding user to Firestore", e)
        }
    }

    override fun getUserInfo(serverResult: (ServerResult<CurrentUser?>) -> Unit) {
        serverResult(ServerResult.Progress)
        Handler(Looper.getMainLooper()).postDelayed({
            var currentUser: CurrentUser?
            val user=FirebaseAuth.getInstance().currentUser
            if (!user.isNull) {
                firestore.collection(Endpoints.USERS)
                    .document(user?.email!!)
                    .get(Source.SERVER)
                    .addOnSuccessListener { snap ->
                        if (snap != null && snap.exists()) {
                            currentUser = snap.toObject(CurrentUser::class.java)

                            if (currentUser != null) {
                                Timber.tag(TAG).d(currentUser?.USERNAME)
                                Timber.tag(TAG).d(currentUser?.EMAIL)
                                Timber.tag(TAG).d(currentUser?.PROJECTS.toString())
                                serverResult(ServerResult.Success(currentUser))
                            }

                            serverResult(ServerResult.Success(currentUser))
                        } else {
                            serverResult(ServerResult.Failure(Exception("User Not Found")))
                        }
                    }
                    .addOnFailureListener { error ->
                        Timber.tag(TAG).e(error, "Failed to retrieve user information")
                        serverResult(ServerResult.Failure(error))
                    }
            }
        }, 1000)
    }

    override fun getUserInfoEditProfile(serverResult: (ServerResult<UserInfo?>) -> Unit) {

        serverResult(ServerResult.Progress)

        var userInfo: UserInfo?
        firestore.collection(Endpoints.USERS)
            .document(FirebaseAuth.getInstance().currentUser?.email!!)
            .get(Source.SERVER)
            .addOnSuccessListener { snap ->

                if (snap.exists()) {
                    userInfo = snap.toObject(UserInfo::class.java)

                    Timber.tag(TAG).d(userInfo?.USERNAME)
                    Timber.tag(TAG).d(userInfo?.DESIGNATION)
                    Timber.tag(TAG).d(userInfo?.BIO)
                    Timber.tag(TAG).d(userInfo?.DP_URL)

                    serverResult(ServerResult.Success(userInfo))
                }
            }
            .addOnFailureListener { error ->
                Timber.tag(TAG).d("failed %s", error.stackTrace)
                serverResult(ServerResult.Failure(error))
            }
    }

    override fun editUserInfo(
        userInfo: UserInfo,
        serverResult: (ServerResult<UserInfo?>) -> Unit
    ) {

        serverResult(ServerResult.Progress)

        val userUpdate = mapOf(
            "USERNAME" to userInfo.USERNAME,
            "BIO" to userInfo.BIO,
            "DESIGNATION" to userInfo.DESIGNATION,
            "DP_URL" to userInfo.DP_URL
        )

        firestore.collection(Endpoints.USERS)
            .document(FirebaseAuth.getInstance().currentUser?.email!!)
            .update(userUpdate)
            .addOnSuccessListener { snap ->
                serverResult(ServerResult.Success(userInfo))
            }
            .addOnFailureListener { error ->
                Timber.tag(TAG).d("failed %s", error.stackTrace)
                serverResult(ServerResult.Failure(error))
            }

    }


    override fun fetchUserProjectIDs(projectListCallback: (ServerResult<List<String>>) -> Unit) {
        getUserInfo { result ->

            when (result) {


                is ServerResult.Failure -> {
                    projectListCallback(ServerResult.Failure(result.exception))
                }

                ServerResult.Progress -> {
                    projectListCallback(ServerResult.Progress)

                }

                is ServerResult.Success -> {
                    projectListCallback(ServerResult.Success(result.data!!.PROJECTS))
                }
            }

        }


    }


    override fun createSegment(segment: Segment, serverResult: (ServerResult<Int>) -> Unit) {
        return try {

            serverResult(ServerResult.Progress)
            firestore.collection(Endpoints.PROJECTS)
                .document(segment.project_ID).collection(Endpoints.Project.SEGMENT)
                .document(segment.segment_NAME).set(segment)
            serverResult(ServerResult.Success(200))
        } catch (exception: Exception) {
            serverResult(ServerResult.Failure(exception))
        }
    }


    // TODO : Use Where Query
    override fun checkIfSegmentNameExists(
        fieldName: String,
        projectID: String,
        result: (ServerResult<Boolean>) -> Unit
    ) {

        result(ServerResult.Progress)

        getProjectRef(projectID).collection(Endpoints.Project.SEGMENT).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->

                CoroutineScope(Dispatchers.IO).launch {
                    for (document in snapshot.documents) {
                        val fieldValue = document.getString("segment_NAME")
                        if (fieldValue == fieldName) {
                            result(ServerResult.Success(true))
                            return@launch
                        }
                    }

                    result(ServerResult.Success(false))
                }

            }
            .addOnFailureListener {
                Timber.tag(TAG).d("Firestore Exception : ${it}")
                result(ServerResult.Failure(it))
            }
    }


    fun getSegments(
        projectName: String, result: (ServerResult<List<Segment>>) -> Unit
    ) {
        firestore.collection(Endpoints.PROJECTS).document(projectName)
            .collection(Endpoints.Project.SEGMENT)
            .get(Source.SERVER)
            .addOnSuccessListener { querySnapshot ->
                val segment_list = mutableListOf<Segment>()

                CoroutineScope(Dispatchers.IO).launch {
                    for (document in querySnapshot.documents) {
                        val segments = document.toObject(Segment::class.java)
                        segment_list.add(segments!!)
                    }

                    withContext(Dispatchers.Main) {
                        Timber.d("segements", segment_list.toString())
                        result(ServerResult.Success(segment_list))
                    }

                }


            }
            .addOnFailureListener { exception ->
                result(ServerResult.Failure(exception))
            }
    }

    fun getTasks(
        projectName: String,
        segmentName: String,
        sectionName: String,
        result: (ServerResult<List<Task>>) -> Unit
    ) {

        firestore.collection(Endpoints.PROJECTS)
            .document(projectName)
            .collection(Endpoints.Project.TASKS)
            .whereEqualTo("section", sectionName)
            .whereEqualTo("segment", segmentName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val sectionList = mutableListOf<Task>()

                CoroutineScope(Dispatchers.IO).launch {
                    for (document in querySnapshot.documents) {
                        val sectionData = document.toObject(Task::class.java)
                        sectionData?.let { sectionList.add(it) }
                    }
                }

                result(ServerResult.Success(sectionList))

            }
            .addOnFailureListener { exception ->
                result(ServerResult.Failure(exception))
            }
    }

    override suspend fun getTasksinProject(
        projectName: String,
    ): ServerResult<List<Task>> {

        return try {

            val task =
                firestore.collection(Endpoints.PROJECTS)
                    .document(projectName)
                    .collection(Endpoints.Project.TASKS)
                    .get().await()

            val snapShot = task

            if (!snapShot.isEmpty) {

                val list:MutableList<Task> = mutableListOf()
                for (document in snapShot.documents){
                    val taskData = document.toObject(Task::class.java)
                    list.add(taskData!!)
                }

                list?.let {
                    return ServerResult.Success(it)
                } ?: ServerResult.Failure(Exception("Document not found for title:"))

            } else {
                return ServerResult.Failure(Exception("Document not found for title"))
            }

        } catch (e: Exception) {
            return ServerResult.Failure(e)
        }

    }

    override suspend fun getTagsinProject(
        projectName: String,
    ): ServerResult<List<Tag>> {

        return try {

            val task =
                firestore.collection(Endpoints.PROJECTS)
                    .document(projectName)
                    .collection(Endpoints.Project.TAGS)
                    .get().await()

            val snapShot = task

            if (!snapShot.isEmpty) {

                val list:MutableList<Tag> = mutableListOf()
                for (document in snapShot.documents){
                    val tagText = document.getString("tagText")
                    val tagID = document.getString("tagID")
                    val textColor = document.getString("textColor")!!
                    val bgColor = document.getString("bgColor")
                    val projectName=document.getString("projectName")

                    val lastUpdate:Timestamp
                    if (document.get(Endpoints.Project.LAST_TAG_UPDATED).isNull){
                        lastUpdate=Timestamp.now()
                    }
                    else{
                        lastUpdate=document.get(Endpoints.Project.LAST_TAG_UPDATED) as Timestamp
                    }
                    val tagData = Tag(
                        tagText = tagText!!,
                        tagID = tagID!!,
                        textColor = textColor,
                        bgColor = bgColor!!,
                        last_tag_updated = lastUpdate,
                        projectName = projectName!!
                    )
                    list.add(tagData)
                }

                list.let {
                    return ServerResult.Success(it)
                } ?: ServerResult.Failure(Exception("Document not found for title:"))

            } else {
                return ServerResult.Failure(Exception("Document not found for title"))
            }

        } catch (e: Exception) {
            return ServerResult.Failure(e)
        }

    }

    override suspend fun getTasksItem(
        projectName: String,
        segmentName: String,
        sectionName: String,
        result: (ServerResult<List<TaskItem>>) -> Unit
    ): Unit {

        try {
            val snapshots = firestore.collection(Endpoints.PROJECTS)
                .document(projectName)
                .collection(Endpoints.Project.TASKS)
                .whereEqualTo("section", sectionName)
                .whereEqualTo("segment", segmentName)
                .get().await()


            val sectionList = mutableListOf<TaskItem>()
            var assignerID: String

            val finalList = CoroutineScope(Dispatchers.IO).async {

                for (document in snapshots.documents) {

                    val title = document.getString("title")
                    val id = document.getString("id")
                    val difficulty = document.get("difficulty")!!
                    val time: Timestamp =
                        (document.get("time_STAMP") ?: Timestamp.now()) as Timestamp

                    val completed = document.getBoolean("completed")
                    assignerID = if (document.getString("assigner_email") != null) {
                        document.getString("assigner_email")!!
                    } else {
                        "mohit@mail.com"
                    }

                    val taskItem = TaskItem(
                        title = title!!,
                        id = id!!,
                        difficulty = difficulty.toString().toInt(),
                        timestamp = time,
                        completed = completed.toString().toBoolean(),
                        assignee_id = assignerID,
                    )

                    sectionList.add(taskItem)
                }
                sectionList
            }


            result(ServerResult.Success(finalList.await()))

        } catch (e: Exception) {
            result(ServerResult.Failure(e))
        }
    }


    override suspend fun fetchProjectTags(
        projectName: String,
        result: (ServerResult<List<Tag>>) -> Unit
    ) {
        firestore.collection(Endpoints.PROJECTS).document(projectName)
            .collection(Endpoints.Project.TAGS)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val tags_list = mutableListOf<Tag>()
                for (document in querySnapshot.documents) {
                    val tag = document.toObject(Tag::class.java)
                    tags_list.add(tag!!)
                }
                result(ServerResult.Success(tags_list))
            }
            .addOnFailureListener { exception ->
                result(ServerResult.Failure(exception))
            }

    }

    fun getContributors(projectName: String, result: (ServerResult<List<String>>) -> Unit) {
        firestore.collection(Endpoints.PROJECTS)
            .whereEqualTo("PROJECT_NAME", projectName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val contributors=document.get("contributors") as List<String>
                    result(ServerResult.Success(contributors))
                }
            }
            .addOnFailureListener { exception ->
                result(ServerResult.Failure(exception))
            }
    }

    override fun getUserInfobyId(id: String, serverResult: (ServerResult<User?>) -> Unit) {

        firestore.collection(Endpoints.USERS)
            .whereEqualTo("EMAIL", id)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {

                    val document = querySnapshot.documents[0]
                    val firebaseID = document.getString(Endpoints.User.EMAIL) ?: "mohit@mail"
                    val profileDPUrl = document.getString(Endpoints.User.DP_URL) ?: ""
                    val name = document.getString(Endpoints.User.USERNAME) ?: Errors.AccountErrors.ACCOUNT_FIELDS_NULL.code
                    var time:Timestamp=Timestamp.now()
                    if ( document.get(Endpoints.User.TIMESTAMP).isNull){
                        time=Timestamp.now()
                    }
                    else{
                        time=document.get(Endpoints.User.TIMESTAMP) as Timestamp
                    }
                    val role : Int = document.get(Endpoints.User.ROLE)?.toString()?.toInt() ?: 1
                    val designation = document.getString(Endpoints.User.DESIGNATION) ?: Errors.AccountErrors.ACCOUNT_FIELDS_NULL.code
                    val fcmToken  = document.getString(Endpoints.User.FCM_TOKEN)



                    val user = User(
                        firebaseID = firebaseID,
                        profileDPUrl = profileDPUrl,
                        username = name,
                        timestamp = time,
                        designation = designation,
                        role = role,
                        fcmToken = fcmToken,
                    )

                    serverResult(ServerResult.Success(user))
                } else {
                    serverResult(ServerResult.Failure(Exception("Document not found for title: $id")))
                }
            }
            .addOnFailureListener { exception ->
                serverResult(ServerResult.Failure(exception))
            }
    }

    override fun getUserTasks(
        sectionName: String,
        serverResult: (ServerResult<List<WorkspaceTaskItem>?>) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser?.email!!
        firestore.collection(Endpoints.USERS)
            .document(user)
            .collection(Endpoints.Workspace.WORKSPACE)
            .whereEqualTo(Endpoints.Workspace.STATUS, sectionName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val workspaceTaskItemList = mutableListOf<WorkspaceTaskItem>()

                for (document in querySnapshot.documents) {
                    val id = document.getString(Endpoints.Workspace.ID)
                    val status = document.getString(Endpoints.Workspace.STATUS)
                    val workspaceTaskItem = WorkspaceTaskItem(id = id!!, status = status!!)
                    workspaceTaskItemList.add(workspaceTaskItem)
                }

                serverResult(ServerResult.Success(workspaceTaskItemList))
            }
            .addOnFailureListener { exception ->
                serverResult(ServerResult.Failure(exception))
            }
    }


    override suspend fun postTags(
        tag: Tag,
        projectName: String,
        serverResult: (ServerResult<Int>) -> Unit
    ) {


        return try {

            serverResult(ServerResult.Progress)
            firestore.collection(Endpoints.PROJECTS)
                .document(projectName).collection(Endpoints.Project.TAGS).document(tag.tagID!!)
                .set(tag)
                .await()
            firestore.collection(Endpoints.PROJECTS)
                .document(projectName)
                .update(Endpoints.Project.LAST_TAG_UPDATED, Timestamp.now())
                .await()
            PrefManager.putLastTAGCacheUpdateTimestamp(Timestamp.now())
            CoroutineScope(Dispatchers.IO).launch {
                db.tagsDao().insert(tag)
            }
            serverResult(ServerResult.Success(200))

        } catch (exception: Exception) {
            serverResult(ServerResult.Failure(exception))
        }

    }

    override suspend fun getTasksbyId(
        id: String,
        projectName: String,
    ): ServerResult<Task> {

        return try {

            val task =
                firestore.collection(Endpoints.PROJECTS)
                    .document(projectName)
                    .collection(Endpoints.Project.TASKS)
                    .whereEqualTo("id", id)
                    .get().await()

            val snapShot = task

            if (!snapShot.isEmpty) {

                val document = snapShot.documents[0]
                val taskData = document.toObject(Task::class.java)

                taskData?.let {
                    return ServerResult.Success(it)
                } ?: ServerResult.Failure(Exception("Document not found for title: $id"))

            } else {
                return ServerResult.Failure(Exception("Document not found for title: $id"))
            }

        } catch (e: Exception) {
            return ServerResult.Failure(e)
        }

    }

    override fun getSection(
        projectName: String,
        segmentName: String,
        result: (ServerResult<List<*>>) -> Unit
    ) {

        firestore.collection(Endpoints.PROJECTS).document(projectName)
            .collection(Endpoints.Project.SEGMENT).document(segmentName)
            .get()
            .addOnSuccessListener {
//                val section_list = mutableListOf<String>()
                if (it.exists()) {
                    val section_list = it.get("sections") as List<*>
                    result(ServerResult.Success(section_list))
                }
            }
            .addOnFailureListener { exception ->
                result(ServerResult.Failure(exception))
            }
    }

    fun getTaskItembyId(
        id: String,
        projectName: String,
        result: (ServerResult<TaskItem>) -> Unit
    ) {

        firestore.collection(Endpoints.PROJECTS)
            .document(projectName)
            .collection(Endpoints.Project.TASKS)
            .whereEqualTo("id", id)
            .get()
            .addOnSuccessListener { querySnapshot1 ->
                if (querySnapshot1.documents.isEmpty()) {
                    // Log or handle the case where no document is found
                    Log.d("debuf", "No document found with ID: $id")
                    result(ServerResult.Failure(Exception("Document not found")))
                } else {
                    val querySnapshot = querySnapshot1.documents[0]
                    val assignerID: String
                    val title = querySnapshot.getString("title")
                    val id = querySnapshot.getString("id")
                    val difficulty = querySnapshot.get("difficulty")
                    val time = querySnapshot.get("time_STAMP") as Timestamp
                    val completed = querySnapshot.getBoolean("completed")
                    if (querySnapshot.getString("assigner_email") != null) {
                        assignerID = querySnapshot.getString("assigner_email")!!
                    } else {
                        assignerID = "mohit@mail.com"
                    }

                    val taskItem = TaskItem(
                        title = title!!,
                        id = id!!,
                        difficulty = difficulty.toString().toInt(),
                        timestamp = time,
                        completed = completed.toString().toBoolean(),
                        assignee_id = assignerID,
                    )
                    Log.d("taskrepo", taskItem.toString())
                    result(ServerResult.Success(taskItem))
                }

            }

            .addOnFailureListener { exception ->
                result(ServerResult.Failure(exception))
            }
    }

    override suspend fun postMessage(
        projectName: String,
        taskId: String,
        message: Message,
        serverResult: (ServerResult<Int>) -> Unit
    ) {


        return try {

            serverResult(ServerResult.Progress)
            firestore.collection(Endpoints.PROJECTS)
                .document(projectName)
                .collection(Endpoints.Project.TASKS)
                .document(taskId)
                .collection(Endpoints.Project.MESSAGES)
                .document(message.messageId)
                .set(message)
                .await()

            serverResult(ServerResult.Success(200))

        } catch (exception: Exception) {
            serverResult(ServerResult.Failure(exception))
        }
    }

    override fun getMessages(
        projectName: String,
        taskId: String,
        result: (ServerResult<List<Message>>) -> Unit
    ) {
        firestore.collection(Endpoints.PROJECTS)
            .document(projectName)
            .collection(Endpoints.Project.TASKS)
            .document(taskId)
            .collection(Endpoints.Project.MESSAGES)
            .addSnapshotListener { querySnapshot, exception ->

                if (exception != null) {
                    result(ServerResult.Failure(exception))
                    return@addSnapshotListener
                }

                val messageList = mutableListOf<Message>()

                querySnapshot?.let { snapshot ->
                    for (newDocs in snapshot.documentChanges) {

                        val document = newDocs.document

                        val messageId = document.getString("messageId")
                        val senderId = document.getString("senderId")
                        val content = document.getString("content")
                        val timestamp = document.getTimestamp("timestamp")
                        val messageType = document.getString("messageType")
                        val additionalData = document.get("additionalData") as HashMap<String, Any>

                        val messageData = Message(
                            messageId!!,
                            senderId!!,
                            content!!,
                            timestamp!!,
                            com.ncs.o2.Domain.Models.Enums.MessageType.fromString(messageType!!)!!,
                            additionalData
                        )
                        Timber.tag(TAG).d("NM123 : $messageData")

                        messageData.let { messageList.add(it) }
                    }
                }

                result(ServerResult.Success(messageList))
            }
    }

    override fun getProjectLink(
        projectName: String,
        result: (ServerResult<String>) -> Unit
    ) {
        result(ServerResult.Progress)
        firestore.collection(Endpoints.PROJECTS)
            .document(projectName)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                val link = documentSnapshot.getString("PROJECT_LINK")
                result(ServerResult.Success(link!!))
            }
            .addOnFailureListener {
                result(ServerResult.Failure(it))

            }

    }


    override fun getMessageUserInfobyId(
        id: String,
        serverResult: (ServerResult<UserInMessage>) -> Unit
    ) {

        firestore.collection(Endpoints.USERS)
            .whereEqualTo("EMAIL", id)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val EMAIL = document.getString("EMAIL")
                    val DP_URL = document.getString("DP_URL")
                    val USERNAME = document.getString("USERNAME")!!
                    val user = UserInMessage(
                        EMAIL = EMAIL!!, USERNAME = USERNAME, DP_URL = DP_URL
                    )
                    serverResult(ServerResult.Success(user))
                } else {
                    serverResult(ServerResult.Failure(Exception("Document not found for title: $id")))
                }
            }
            .addOnFailureListener { exception ->
                serverResult(ServerResult.Failure(exception))
            }
    }

    override fun postImage(bitmap: Bitmap,projectId:String,taskId:String): LiveData<ServerResult<StorageReference>> {

        val liveData = MutableLiveData<ServerResult<StorageReference>>()
        val imageFileName = generateRandomID(IDType.SegmentID)
        val imageRef = storageReference.child(projectId).child("TASKS").child(taskId).child(imageFileName)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val data = baos.toByteArray()
        val uploadTask = imageRef.putBytes(data)

        uploadTask.addOnSuccessListener {
            liveData.postValue(ServerResult.Success(imageRef))

        }.addOnFailureListener { exception ->
            liveData.postValue(ServerResult.Failure(exception))

        }

        return liveData
    }
    override suspend fun updateTask(
        id: String,
        projectName: String,
        newAssignee: String,
        oldAssignee: String,
    ): ServerResult<Boolean> {
        try {
            firestore.runTransaction { transaction ->
                val documentRef = firestore.collection(Endpoints.PROJECTS)
                    .document(projectName)
                    .collection(Endpoints.Project.TASKS)
                    .document(id)

                val documentSnapshot = transaction.get(documentRef)

                if (documentSnapshot.exists()) {
                    if (oldAssignee != "None") {
                        val oldAssigneeWorkspaceRef =
                            firestore.collection(Endpoints.USERS).document(oldAssignee)
                                .collection(Endpoints.Workspace.WORKSPACE).document(id)
                        transaction.delete(oldAssigneeWorkspaceRef)
                    }

                    transaction.update(documentRef, "assignee", newAssignee)
                    transaction.update(documentRef, "status", 2)

                    val workspaceItem = WorkspaceTaskItem(id = id, status = "Assigned")

                    if (newAssignee != "None") {
                        val newAssigneeWorkspaceRef =
                            firestore.collection(Endpoints.USERS).document(newAssignee)
                                .collection(Endpoints.Workspace.WORKSPACE).document(id)
                        transaction.set(newAssigneeWorkspaceRef, workspaceItem)
                    }

                    updateLastUpdated(projectName, transaction)
                    updateTaskLastUpdated(projectName,transaction,id)
                    true
                } else {
                    false
                }
            }

            return ServerResult.Success(true)
        } catch (e: Exception) {
            return ServerResult.Failure(e)
        }
    }

    override suspend fun updateModerator(
        id: String,
        projectName: String,
        moderator: String
    ): ServerResult<Unit> {
        return try {
            val result = withContext(Dispatchers.IO) {
                val task = db.tasksDao().getTasksbyId(id, projectName)
                task?.moderators?.toMutableList()?.add(moderator)
                db.tasksDao().update(task!!)
            }

            firestore.runTransaction { transaction ->
                val documentRef = firestore.collection(Endpoints.PROJECTS)
                    .document(projectName)
                    .collection(Endpoints.Project.TASKS)
                    .document(id)

                val updateData = mapOf(
                    "moderators" to FieldValue.arrayUnion(moderator)
                )

                transaction.update(documentRef, updateData)
                updateLastUpdated(projectName, transaction)
                updateTaskLastUpdated(projectName, transaction, id)
                true
            }

            ServerResult.Success(result)
        } catch (e: Exception) {
            ServerResult.Failure(e)
        }
    }


    override suspend fun updateTags(
        newTags:List<String>,
        projectName: String,
        taskId: String,
    ): ServerResult<Boolean> {
        return try {

            firestore.runTransaction { transaction ->
                firestore.collection(Endpoints.PROJECTS)
                    .document(projectName)
                    .collection(Endpoints.Project.TASKS)
                    .document(taskId)
                    .update("tags", newTags)
                updateLastUpdated(projectName, transaction)
                updateTaskLastUpdated(projectName, transaction, taskId)
                true
            }

            ServerResult.Success(true)
        } catch (e: Exception) {
            ServerResult.Failure(e)
        }
    }


    override suspend fun addNewModerator(
        id: String,
        projectName: String,
        newModerators: MutableList<String>,
        unselected: MutableList<String>
    ): ServerResult<Boolean> {
        try {
            firestore.runTransaction { transaction ->
                val documentRef = firestore.collection(Endpoints.PROJECTS)
                    .document(projectName)
                    .collection(Endpoints.Project.TASKS)
                    .document(id)

                val existingModeratorsList = transaction.get(documentRef).get("moderators") as? MutableList<String>
                    ?: mutableListOf()

                existingModeratorsList.removeAll(unselected)

                existingModeratorsList.addAll(newModerators)

                val finalModeratorsList = existingModeratorsList.distinct()

                transaction.update(documentRef, "moderators", finalModeratorsList)

                updateLastUpdated(projectName,transaction)
                updateTaskLastUpdated(projectName,transaction,id)
                true
            }

            return ServerResult.Success(true)
        } catch (e: Exception) {
            return ServerResult.Failure(e)
        }
    }



    override suspend fun updateState(
        id: String,
        userID: String,
        newState: String,
        projectName: String
    ): ServerResult<Boolean> {
        try {
            firestore.runTransaction { transaction ->
                val userDocumentRef = firestore.collection(Endpoints.USERS)
                    .document(userID)
                    .collection(Endpoints.Workspace.WORKSPACE)
                    .document(id)

                val projectDocumentRef = firestore.collection(Endpoints.PROJECTS)
                    .document(PrefManager.getcurrentProject())
                    .collection(Endpoints.Project.TASKS)
                    .document(id)

                val userDocumentSnapshot = transaction.get(userDocumentRef)

                if (userDocumentSnapshot.exists()) {
                    val status = when (newState) {
                        "Submitted" -> 1
                        "Open" -> 2
                        "Working" -> 3
                        "Review" -> 4
                        "Completed" -> 5
                        "Assigned" -> 2
                        else -> -1
                    }

                    transaction.update(userDocumentRef, "status", newState)
                    transaction.update(projectDocumentRef, "status", status)
                    updateLastUpdated(projectName,transaction)
                    updateTaskLastUpdated(projectName,transaction,id)

                } else {
                    val status = when (newState) {
                        "Submitted" -> 1
                        "Open" -> 2
                        "Working" -> 3
                        "Review" -> 4
                        "Completed" -> 5
                        else -> -1
                    }

                    transaction.update(projectDocumentRef, "status", status)

                    updateLastUpdated(projectName,transaction)
                    updateTaskLastUpdated(projectName,transaction,id)

                }

                true
            }

            return ServerResult.Success(true)
        } catch (e: Exception) {
            return ServerResult.Failure(e)
        }
    }

    override suspend fun updatePriority(
        id: String,
        newPriority: String,
        projectName: String
    ): ServerResult<Boolean> {
        try {
            firestore.runTransaction { transaction ->


                val projectDocumentRef = firestore.collection(Endpoints.PROJECTS)
                    .document(PrefManager.getcurrentProject())
                    .collection(Endpoints.Project.TASKS)
                    .document(id)

                val priority = SwitchFunctions.getNumPriorityFromStringPriority(newPriority)
                transaction.update(projectDocumentRef, "priority", priority)
                updateLastUpdated(projectName,transaction)
                updateTaskLastUpdated(projectName,transaction,id)

                true
            }

            return ServerResult.Success(true)
        } catch (e: Exception) {
            return ServerResult.Failure(e)
        }
    }

    override fun getCheckList(
        projectName: String,
        taskId: String,
        result: (ServerResult<List<CheckList>>) -> Unit
    ) {

        firestore.collection(Endpoints.PROJECTS).document(projectName)
            .collection(Endpoints.Project.TASKS).document(taskId).collection(Endpoints.Project.CHECKLIST)
            .get()
            .addOnSuccessListener {
                val CheckListArray = mutableListOf<CheckList>()
                for (document in it.documents) {
                    val id=document.getString("id")
                    val title=document.getString("title")
                    val desc=document.getString("desc")
                    val done=document.getBoolean("done")
                    val index=document.getLong("index")
                    val checkList=CheckList(id = id!!, title = title!!, desc = desc!!, done = done!!, index = index!!.toInt())
                    CheckListArray.add(checkList)
                }
                result(ServerResult.Success(CheckListArray))
            }
            .addOnFailureListener { exception ->
                result(ServerResult.Failure(exception))
            }
    }

    override suspend fun updateCheckListCompletion(
        taskId: String,
        projectName: String,
        id: String,
        done: Boolean,
    ): ServerResult<Boolean> {
        try {
            firestore.runTransaction { transaction ->
                val documentRef = firestore.collection(Endpoints.PROJECTS)
                    .document(projectName)
                    .collection(Endpoints.Project.TASKS)
                    .document(taskId)
                    .collection(Endpoints.Project.CHECKLIST)
                    .document(id)

                transaction.update(documentRef, "done", done)

                updateLastUpdated(projectName, transaction)
                updateTaskLastUpdated(projectName,transaction,id)

                true
            }

            return ServerResult.Success(true)
        } catch (e: Exception) {
            return ServerResult.Failure(e)
        }
    }


    override suspend fun updateCheckList(
        taskId: String,
        projectName: String,
        id: String,
        checkList: CheckList
    ): ServerResult<Boolean> {
        try {
            firestore.runTransaction { transaction ->
                val documentRef = firestore.collection(Endpoints.PROJECTS)
                    .document(projectName)
                    .collection(Endpoints.Project.TASKS)
                    .document(taskId)
                    .collection(Endpoints.Project.CHECKLIST)
                    .document(id)

                transaction.set(documentRef, checkList)

                updateLastUpdated(projectName, transaction)
                updateTaskLastUpdated(projectName,transaction,id)

                true
            }

            return ServerResult.Success(true)
        } catch (e: Exception) {
            return ServerResult.Failure(e)
        }
    }

    override suspend fun updateSection(
        taskId: String,
        projectName: String,
        newSection: String,
    ): ServerResult<Boolean> {
        try {
            firestore.runTransaction { transaction ->
                val documentRef = firestore.collection(Endpoints.PROJECTS)
                    .document(projectName)
                    .collection(Endpoints.Project.TASKS)
                    .document(taskId)

                transaction.update(documentRef, "section", newSection)

                updateLastUpdated(projectName, transaction)
                updateTaskLastUpdated(projectName,transaction,taskId)

                true
            }

            return ServerResult.Success(true)
        } catch (e: Exception) {
            return ServerResult.Failure(e)
        }
    }
    override suspend fun initilizelistner(projectName: String,result: (ServerResult<Int>) -> Unit) {
        val projectDocument = FirebaseFirestore.getInstance().collection(Endpoints.PROJECTS).document(projectName)
        val listenerRegistration = projectDocument.addSnapshotListener { snapshot: DocumentSnapshot?, exception: FirebaseFirestoreException? ->
            if (exception != null) {
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists() && snapshot.contains(Endpoints.Project.LAST_UPDATED)) {
                val lastUpdatedValue = snapshot.get(Endpoints.Project.LAST_UPDATED)
                firestore.collection(Endpoints.PROJECTS)
                    .document(projectName)
                    .collection(Endpoints.Project.TASKS)
                    .whereGreaterThan(Endpoints.Project.LAST_UPDATED, PrefManager.getLastCacheUpdateTimestamp())
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        val sectionList = mutableListOf<Task>()
                        CoroutineScope(Dispatchers.IO).launch {
                            for (document in querySnapshot.documents) {
                                val sectionData = document.toObject(Task::class.java)
                                PrefManager.putLastCacheUpdateTimestamp(Timestamp.now())
                                if (db.tasksDao().getTasksbyId(sectionData?.id!!,sectionData?.project_ID!!).isNull){
                                    db.tasksDao().insert(sectionData)
                                }
                                else{
                                    Log.d("update","old task by:  ${db.tasksDao().getTasksbyId(sectionData?.id!!,sectionData?.project_ID!!)}")
                                    db.tasksDao().update(task = sectionData!!)
                                    Log.d("update","updated task by: ${db.tasksDao().getTasksbyId(sectionData?.id!!,sectionData?.project_ID!!)}")
                                }
                            }
                        }

                        result(ServerResult.Success(1))

                    }
                    .addOnFailureListener { exception ->
                        result(ServerResult.Failure(exception))
                    }
            }
        }
    }

    override suspend fun initilizeTagslistner(projectName: String,result: (ServerResult<Int>) -> Unit) {
        val projectDocument = FirebaseFirestore.getInstance().collection(Endpoints.PROJECTS).document(projectName)
        val listenerRegistration = projectDocument.addSnapshotListener { snapshot: DocumentSnapshot?, exception: FirebaseFirestoreException? ->
            if (exception != null) {
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists() && snapshot.contains(Endpoints.Project.LAST_TAG_UPDATED)) {
                val lastUpdatedValue = snapshot.get(Endpoints.Project.LAST_TAG_UPDATED)
                firestore.collection(Endpoints.PROJECTS)
                    .document(projectName)
                    .collection(Endpoints.Project.TAGS)
                    .whereGreaterThan(Endpoints.Project.LAST_TAG_UPDATED, PrefManager.getLastTAGCacheUpdateTimestamp())
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        val tagList = mutableListOf<Tag>()
                        CoroutineScope(Dispatchers.IO).launch {
                            for (document in querySnapshot.documents) {
                                val tagText = document.getString("tagText")
                                val tagID = document.getString("tagID")
                                val textColor = document.getString("textColor")!!
                                val bgColor = document.getString("bgColor")
                                val projectName=document.getString("projectName")
                                val lastUpdate:Timestamp
                                if (document.get(Endpoints.Project.LAST_TAG_UPDATED).isNull){
                                    lastUpdate=Timestamp.now()
                                }
                                else{
                                    lastUpdate=document.get(Endpoints.Project.LAST_TAG_UPDATED) as Timestamp
                                }
                                val tagData = Tag(
                                    tagText = tagText!!,
                                    tagID = tagID!!,
                                    textColor = textColor,
                                    bgColor = bgColor!!,
                                    last_tag_updated = lastUpdate,
                                    projectName = projectName!!
                                )
                                PrefManager.putLastTAGCacheUpdateTimestamp(Timestamp.now())
                                if (db.tagsDao().getTagbyId(tagData?.tagID!!).isNull){
                                    db.tagsDao().insert(tagData)
                                }
                                else{
                                    Log.d("update","old tag by:  ${db.tagsDao().getTagbyId(tagData?.tagID!!)}")
                                    db.tagsDao().update(tag = tagData)
                                    Log.d("update","updated tag by: ${db.tagsDao().getTagbyId(tagData?.tagID!!)}")
                                }
                            }
                        }

                        result(ServerResult.Success(1))

                    }
                    .addOnFailureListener { exception ->
                        result(ServerResult.Failure(exception))
                    }
            }
        }
    }

    override suspend fun getSearchedTasks(
        assignee: String,
        creator: String,
        state: Int,
        type: Int,
        text: String,
        projectName: String,
        result: (ServerResult<List<TaskItem>>) -> Unit
    ) {
        var query:Query= firestore.collection(Endpoints.PROJECTS)
            .document(projectName)
            .collection(Endpoints.Project.TASKS)
        if (type != 0) {
            query = query.whereEqualTo("type", type)
        }
        if (state != 0) {
            query = query.whereEqualTo("status", state)
        }
        if (creator.isNotEmpty()){
            query = query.whereEqualTo("assigner", creator)
        }
        if (assignee.isNotEmpty()){
            query = query.whereEqualTo("assignee", assignee)
        }
        query.get()
            .addOnSuccessListener { querySnapshot ->
                val sectionList = mutableListOf<TaskItem>()
                for (document in querySnapshot.documents) {
                    val title = document.getString("title")
                    val id = document.getString("id")
                    val difficulty = document.get("difficulty")!!
                    val time: Timestamp = (document.get("time_STAMP") ?: Timestamp.now()) as Timestamp
                    val completed = document.getBoolean("completed")
                    val assignerID = document.getString("assigner_email") ?: "mohit@mail.com"
                    val taskItem = TaskItem(
                        title = title!!,
                        id = id!!,
                        difficulty = difficulty.toString().toInt(),
                        timestamp = time,
                        completed = completed.toString().toBoolean(),
                        assignee_id = assignerID,
                    )
                    sectionList.add(taskItem)
                }
                result(ServerResult.Success(sectionList))
            }
            .addOnFailureListener { exception ->
                result(ServerResult.Failure(exception))
            }
    }
    fun updateLastUpdated(projectName: String,transaction:Transaction){
        val projectDocRef = firestore.collection(Endpoints.PROJECTS)
            .document(projectName)
        val last_updated=Timestamp.now()
        val projectData = mapOf(
            Endpoints.Project.LAST_UPDATED to last_updated
        )
        transaction.update(projectDocRef, projectData)
    }
    fun updateTaskLastUpdated(projectName: String,transaction:Transaction,taskId: String){
        val projectDocRef = firestore.collection(Endpoints.PROJECTS)
            .document(projectName).collection(Endpoints.Project.TASKS).document(taskId)
        val last_updated=Timestamp.now()
        val projectData = mapOf(
            Endpoints.Project.LAST_UPDATED to last_updated
        )
        transaction.update(projectDocRef, projectData)
    }
    fun updateLastProjectTagUpdated(projectName: String,transaction:Transaction){
        val projectDocRef = firestore.collection(Endpoints.PROJECTS)
            .document(projectName)
        val last_updated=Timestamp.now()
        val projectData = mapOf(
            Endpoints.Project.LAST_TAG_UPDATED to last_updated
        )
        transaction.update(projectDocRef, projectData)
    }
    fun updateTagLastUpdated(projectName: String,transaction:Transaction,tagId:String){
        val projectDocRef = firestore.collection(Endpoints.PROJECTS)
            .document(projectName).collection(Endpoints.Project.TAGS).document(tagId)
        val last_updated=Timestamp.now()
        val projectData = mapOf(
            Endpoints.Project.LAST_TAG_UPDATED to last_updated
        )
        transaction.update(projectDocRef, projectData)
    }
    suspend fun addProjectToUser(projectLink: String): ServerResult<ArrayList<String>> {
        return try {
            if (projectLink.isNotBlank()) {
                val userDocument = getUserDocument()

                val projectData = getProjectData(projectLink)

                if (projectData != null) {
                    val isProjectAlreadyAdded = checkIfProjectAlreadyAdded(userDocument, projectData)
                    if (isProjectAlreadyAdded) {
                        return ServerResult.Success(ArrayList())
                    }

                    userDocument.update("PROJECTS", FieldValue.arrayUnion(projectData))
                        .await()

                    updateProjectContributors(projectData)
                    PrefManager.lastaddedproject(projectData)

                    val updatedUserProjects = getUserProjects(userDocument)

                    return ServerResult.Success(updatedUserProjects)
                } else {
                    return ServerResult.Failure(Exception("Project not found, please check the link"))
                }
            } else {
                return ServerResult.Failure(Exception("Project Link can't be empty"))
            }
        } catch (e: Exception) {
            return ServerResult.Failure(e)
        }
    }

    private suspend fun getUserDocument(): DocumentReference {
        return firebaseFirestore.collection("Users")
            .document(FirebaseAuth.getInstance().currentUser?.email!!)
    }

    private suspend fun getProjectData(projectLink: String): String? {
        Log.d("projectcheck",projectLink.toString())
        val documents = firebaseFirestore.collection("Projects")
            .whereEqualTo("PROJECT_DEEPLINK", projectLink.toLowerCase())
            .get()
            .await()

        return if (!documents.isEmpty) {
            documents.documents.firstOrNull()?.getString("PROJECT_NAME")
        } else {
            null
        }
    }

    private suspend fun checkIfProjectAlreadyAdded(
        userDocument: DocumentReference,
        projectData: String
    ): Boolean {
        val userSnapshot = userDocument.get().await()
        val userProjects = userSnapshot.get("PROJECTS") as ArrayList<String>?
        return userProjects != null && userProjects.contains(projectData)
    }

    private fun updateProjectContributors(projectData: String) {
        val addContributors = mapOf(
            "contributors" to FieldValue.arrayUnion(FirebaseAuth.getInstance().currentUser?.email)
        )

        firebaseFirestore.collection(Endpoints.PROJECTS)
            .document(projectData)
            .update(addContributors)
    }

    private suspend fun getUserProjects(userDocument: DocumentReference): ArrayList<String> {
        val userSnapshot = userDocument.get().await()
        return userSnapshot.get("PROJECTS") as ArrayList<String>? ?: ArrayList()
    }

}



