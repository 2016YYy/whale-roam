package com.whaleroam.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.whaleroam.MainActivity
import com.whaleroam.R
import com.whaleroam.data.HistoryStore
import com.whaleroam.data.Place
import com.whaleroam.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var store: HistoryStore
    private val list = mutableListOf<Place>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        store = HistoryStore(this)
        list.addAll(store.list())
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = PlaceAdapter(list)
        binding.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    inner class PlaceAdapter(private val data: List<Place>) :
        RecyclerView.Adapter<PlaceAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView = v.findViewById(R.id.placeLabel)
            val coord: TextView = v.findViewById(R.id.placeCoord)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_place, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = data[position]
            holder.label.text = p.label
            holder.coord.text = "%.6f, %.6f".format(p.lat, p.lon)
            holder.itemView.setOnClickListener {
                val i = Intent(this@HistoryActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("extra_lat", p.lat)
                    putExtra("extra_lon", p.lon)
                }
                startActivity(i)
                finish()
            }
            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@HistoryActivity)
                    .setTitle("删除该记录？")
                    .setPositiveButton(R.string.delete) { _, _ ->
                        store.delete(p.id)
                        list.removeAt(position)
                        notifyItemRemoved(position)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
        }

        override fun getItemCount() = data.size
    }
}
