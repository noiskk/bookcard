import { Search, X } from 'lucide-react'

function SearchBar({ value, onChange, onClear, placeholder = "Search books..." }) {
  return (
    <div className="relative">
      <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
        <Search className="w-5 h-5 text-stone-400" />
      </div>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="input-field pl-12 pr-10"
      />
      {value && (
        <button
          onClick={onClear}
          className="absolute inset-y-0 right-0 pr-4 flex items-center text-stone-400 hover:text-stone-600"
        >
          <X className="w-5 h-5" />
        </button>
      )}
    </div>
  )
}

export default SearchBar
