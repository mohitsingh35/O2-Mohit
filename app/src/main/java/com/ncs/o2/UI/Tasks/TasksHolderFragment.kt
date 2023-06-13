package com.ncs.o2.UI.Tasks

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ncs.o2.Domain.Models.Task
import com.ncs.o2.UI.Tasks.TaskDetails.TaskDetailActivity
import com.ncs.o2.UI.UIComponents.Adapters.TaskListAdapter
import com.ncs.o2.databinding.FragmentTasksHolderBinding
import com.ncs.versa.HelperClasses.BounceEdgeEffectFactory
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TasksHolderFragment : Fragment(), TaskListAdapter.OnClickListener {


    private val viewModel: TasksHolderViewModel by viewModels()
    private lateinit var binding: FragmentTasksHolderBinding
    private lateinit var recyclerView: RecyclerView
    private lateinit var taskListAdapter : TaskListAdapter
    private lateinit var taskList: ArrayList<Task>


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTasksHolderBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
    }

    private fun setupViews() {
        setupRecyclerView()
    }

    private fun setupRecyclerView() {



        val task1 = Task("Appbar not working in the new implementation",
        "Have to implement that.","#1234",2, listOf("link1,link2"),3,1, ASSIGNEE = listOf("mod1"),
            "Assigner1","31/2/23", "31/2/23","3H+", emptyList(), PROJECTID = "Versa123", SEGMENT = "SEG1"
        )
        val task2 = Task("Window navigation not working in Versa 2.0",
            "Have to implement that.","#1364",1, listOf("link1,link2"),2,3, listOf("mod1"),
            "Assigner1","31/2/22", "31/2/23","3H+", emptyList(), PROJECTID = "Versa123", SEGMENT = "SEG1"
        )

        taskList = arrayListOf(task1,task2,task1,task2,task1,task2,task1,task2,task1)
        taskList.add(task1)
        taskList.add(task2)

        recyclerView = binding.recyclerView
        taskListAdapter = TaskListAdapter()
        taskListAdapter.setTaskList(taskList)
        taskListAdapter.setOnClickListener(this)


        val layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)

        with(recyclerView){
            this.layoutManager = layoutManager
            adapter = taskListAdapter
            edgeEffectFactory = BounceEdgeEffectFactory()
        }

    }

    override fun onCLick(position: Int, task: Task) {
        startActivity(Intent(requireContext(), TaskDetailActivity::class.java))
    }
}