package com.ncs.o2.UI.Auth.LoginScreen

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.ncs.o2.Domain.Utility.ExtensionsUtil.setOnClickThrottleBounceListener
import com.ncs.o2.Domain.Utility.ExtensionsUtil.showKeyboard
import com.ncs.o2.Domain.Utility.ExtensionsUtil.showKeyboardB
import com.ncs.o2.R
import com.ncs.o2.UI.MainActivity
import com.ncs.o2.databinding.FragmentLoginScreenBinding

class LoginScreenFragment : Fragment() {

    companion object {
        fun newInstance() = LoginScreenFragment()
    }

    private lateinit var viewModel: LoginScreenViewModel
    lateinit var binding: FragmentLoginScreenBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginScreenBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpViews()
    }

    private fun setUpViews() {
        binding.signUp.setOnClickListener {
            findNavController().navigate(R.id.action_loginScreenFragment_to_signUpScreenFragment)

           // binding.etEmailAddress.showKeyboardB()

        }


        binding.toolbar.btnBack.setOnClickThrottleBounceListener{
            findNavController().navigate(R.id.action_loginScreenFragment_to_chooserFragment)
        }

        binding.btnLogin.setOnClickThrottleBounceListener {
            requireActivity().startActivity(Intent(requireContext(),MainActivity::class.java))
        }

    }

    fun hideKeyboard(activity: Activity) {
        val view = activity.currentFocus
        val methodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        assert(view != null)
        methodManager.hideSoftInputFromWindow(view!!.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    fun showKeyboardThis(activity: Activity) {
        val view = activity.currentFocus
        val methodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        assert(view != null)
        methodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

}