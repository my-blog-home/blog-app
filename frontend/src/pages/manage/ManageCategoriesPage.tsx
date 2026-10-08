import CategoryManager from '../../components/CategoryManager'
import { useManage } from './ManageLayout'

/** 분류 관리 (BM-04, CF-08) */
export default function ManageCategoriesPage() {
  const { blog, reloadBlog } = useManage()
  return (
    <>
      <h1>분류 관리</h1>
      <CategoryManager blogId={blog.id} categories={blog.categories} onChanged={reloadBlog} />
    </>
  )
}
