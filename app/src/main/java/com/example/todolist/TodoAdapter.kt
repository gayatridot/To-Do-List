package com.example.todolist

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class TodoAdapter(
    private val context: Context,
    private val todoList: MutableList<TodoItem>,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = todoList.size

    override fun getItem(position: Int): TodoItem = todoList[position]

    override fun getItemId(position: Int): Long = position.toLong()

    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        // 1. Inflate the layout
        val view: View = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_layout, parent, false)

        // 2. Find the Views in item_layout.xml
        val textNumber = view.findViewById<TextView>(R.id.itemNumber)
        val textTask = view.findViewById<TextView>(R.id.itemText)
        val textDateDisplay = view.findViewById<TextView>(R.id.itemDateDisplay) // Left side (Date)
        val textTimeRange = view.findViewById<TextView>(R.id.itemDate)         // Right side (Time)
        val btnEdit = view.findViewById<ImageView>(R.id.btnEdit)
        val btnDelete = view.findViewById<ImageView>(R.id.btnDelete)

        // 3. Get the data for the current position
        val item = todoList[position]

        // 4. Bind the data to the Views
        textNumber.text = "${position + 1}."
        textTask.text = item.task

        // This splits the parts exactly as requested in your screenshot
        textDateDisplay.text = item.datePart // Shows "4 Jan 2026"
        textTimeRange.text = item.timePart   // Shows "12:00 AM - 10:00 AM"

        // 5. Handle Button Clicks
        btnEdit.setOnClickListener {
            onEdit(position)
        }

        btnDelete.setOnClickListener {
            onDelete(position)
        }

        return view
    }
}
