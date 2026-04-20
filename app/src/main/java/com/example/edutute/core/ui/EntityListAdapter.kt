package com.example.edutute.core.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.edutute.databinding.ItemEntityRowBinding

class EntityListAdapter<T : Any>(
    private val itemId: (T) -> String,
    private val titleProvider: (T) -> String,
    private val subtitleProvider: (T) -> String,
    private val metaProvider: (T) -> String = { "" },
    private val showActionButtons: Boolean = true,
    private val onOpen: (T) -> Unit,
    private val onEdit: (T) -> Unit,
    private val onDelete: (T) -> Unit,
) : ListAdapter<T, EntityListAdapter<T>.EntityViewHolder>(EntityDiffCallback(itemId)) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntityViewHolder {
        val binding = ItemEntityRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EntityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EntityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EntityViewHolder(
        private val binding: ItemEntityRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: T) = with(binding) {
            val title = titleProvider(item)
            titleText.text = title
            subtitleText.text = subtitleProvider(item)
            metaText.text = metaProvider(item)
            metaText.isVisible = metaProvider(item).isNotBlank()
            actionButtonsRow.isVisible = showActionButtons
            avatarText.text = title
                .split(' ')
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString(separator = "") { token -> token.first().uppercase() }
                .ifBlank { "#" }
            root.setOnClickListener { onOpen(item) }
            editButton.setOnClickListener { onEdit(item) }
            deleteButton.setOnClickListener { onDelete(item) }
        }
    }

    private class EntityDiffCallback<T : Any>(
        private val itemId: (T) -> String,
    ) : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = itemId(oldItem) == itemId(newItem)

        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = oldItem == newItem
    }
}
