package com.example.mpa23itb234

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.mpa23itb234.databinding.FavouriteViewBinding
import com.example.mpa23itb234.databinding.MoreFeaturesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
class FavouriteAdapter(
    private val context: Context,
    private var musicList: ArrayList<Music>,
    val playNext: Boolean = false   // Cờ xác định nếu adapter dùng cho chức năng playNext hay không
) : RecyclerView.Adapter<FavouriteAdapter.MyHolder>() {

    // ViewHolder giữ tham chiếu đến các view của item trong RecyclerView
    class MyHolder(binding: FavouriteViewBinding) : RecyclerView.ViewHolder(binding.root) {
        val image = binding.songImgFV
        val name = binding.songNameFV
        val root = binding.root
    }

    // Tạo ViewHolder từ layout item favourite_view.xml (được binding qua FavouriteViewBinding)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        return MyHolder(FavouriteViewBinding.inflate(LayoutInflater.from(context), parent, false))
    }

    // Gán dữ liệu cho từng item (ảnh, tên, sự kiện click)
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        holder.name.text = musicList[position].title
        val music = musicList[position]   // ✅ thêm dòng này

        // Load ảnh bìa bài hát dùng thư viện Glide với placeholder nếu chưa tải được ảnh
        Glide.with(context)
            .load(music.artUri)
            .apply(
                RequestOptions()
                    .placeholder(R.drawable.music_player_icon_slash_screen)
                    .error(R.drawable.music_player_icon_slash_screen)
                    .centerCrop()
                    .override(150, 150)
            )
            .into(holder.image)

        if(playNext){
            // Nếu đang ở chế độ playNext, click sẽ phát bài hát này kế tiếp
            holder.root.setOnClickListener {
                val intent = Intent(context, PlayerActivity::class.java)
                intent.putExtra("index", position)
                intent.putExtra("class", "PlayNext")
                ContextCompat.startActivity(context, intent, null)
            }

            // Long click để hiển thị dialog thêm tính năng (ở đây là "Remove" bài hát khỏi PlayNext)
            holder.root.setOnLongClickListener {
                val customDialog = LayoutInflater.from(context).inflate(R.layout.more_features, holder.root, false)
                val bindingMF = MoreFeaturesBinding.bind(customDialog)
                val dialog = MaterialAlertDialogBuilder(context).setView(customDialog)
                    .create()
                dialog.show()
                dialog.window?.setBackgroundDrawable(ColorDrawable(0x99000000.toInt()))  // Nền dialog mờ

                bindingMF.AddToPNBtn.text = "Remove"  // Đổi tên nút thành "Remove"

                // Xử lý click nút Remove
                bindingMF.AddToPNBtn.setOnClickListener {
                    // Không cho xóa bài đang phát hiện tại
                    if(position == PlayerActivity.songPosition)
                        Snackbar.make((context as Activity).findViewById(R.id.linearLayoutPN),
                            "Can't Remove Currently Playing Song.", Snackbar.LENGTH_SHORT).show()
                    else{
                        // Nếu bài hát xóa nằm sau bài đang phát thì giảm vị trí bài đang phát để đồng bộ
                        if(PlayerActivity.songPosition < position && PlayerActivity.songPosition != 0) --PlayerActivity.songPosition
                        PlayNext.playNextList.removeAt(position)
                        PlayerActivity.musicListPA.removeAt(position)
                        notifyItemRemoved(position)
                    }
                    dialog.dismiss()
                }
                true
            }
        } else {
            // Nếu không phải chế độ playNext, click sẽ phát bài hát bình thường từ FavouriteAdapter
            holder.root.setOnClickListener {
                val intent = Intent(context, PlayerActivity::class.java)
                intent.putExtra("index", position)
                intent.putExtra("class", "FavouriteAdapter")
                ContextCompat.startActivity(context, intent, null)
            }
        }
        Log.d("FAV_DEBUG", "Adapter bind: ${musicList[position].title}")
    }

    // Số lượng item trong danh sách
    override fun getItemCount(): Int {
        return musicList.size
    }

    // Cập nhật lại danh sách bài hát yêu thích và refresh RecyclerView
    @SuppressLint("NotifyDataSetChanged")
    fun updateFavourites(newList: ArrayList<Music>){
        musicList = ArrayList()
        musicList.addAll(newList)
        notifyDataSetChanged()
    }
}
