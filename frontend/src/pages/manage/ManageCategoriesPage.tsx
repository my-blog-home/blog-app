import { useSearchParams } from 'react-router-dom'
import CategoryManager from '../../components/CategoryManager'
import { useManage } from './ManageLayout'

/** 분류 관리 (BM-04, CF-08). 내 블로그 화면의 [수정]은 ?edit=분류id로 들어와 그 분류를 바로 고친다 (FR-32) */
export default function ManageCategoriesPage() {
  const { blog, reloadBlog } = useManage()
  const [params] = useSearchParams()
  const editId = Number(params.get('edit')) || null
  return (
    <>
      <h1>분류 관리</h1>
      <CategoryManager blogId={blog.id} categories={blog.categories} onChanged={reloadBlog} initialEditId={editId} />
    </>
  )
}
